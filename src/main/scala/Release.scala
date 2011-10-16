package sbtrelease

import sbt._
import Keys._
import complete.DefaultParsers._

object Release {
  import Utilities._

  lazy val snapshotDependencies = TaskKey[Seq[ModuleID]]("release-snapshot-dependencies")

  lazy val useDefaults = SettingKey[Boolean]("release-use-defaults")

  type Versions = (String, String)
  type ReleasePart = State => State

  lazy val versions = SettingKey[Versions]("release-versions")
  lazy val releaseProcess = SettingKey[Seq[ReleasePart]]("release-process")


  def releaseTask[T](key: ScopedTask[T]): ReleasePart = { st =>
    Project.extract(st).evalTask(key, st)
    st
  }

  lazy val releaseCommandKey = "release"
  val releaseParser = (Space ~> "with-defaults")?
  val releaseCommand: Command = Command(releaseCommandKey)(_ => releaseParser) { (st, switch) =>
    val extracted = Project.extract(st)
    val process = extracted.get(releaseProcess)

    val startState = extracted.append(Seq(useDefaults := switch.isDefined), st)

    (startState /: process)((st, part) => part(st))
  }

  lazy val initialGitChecksCommandKey = "release-git-checks"
  lazy val initialGitChecksCommand = Command.command(initialGitChecksCommandKey)(initialGitChecks)
  lazy val initialGitChecks: ReleasePart = { st =>
    if (!new File(".git").exists) {
      sys.error("Aborting release. Working directory is not a git repository.")
    }
    val status = (Git.status !!).trim
    if (!status.isEmpty) {
      sys.error("Aborting release. Working directory is dirty.")
    }
    st
  }

  lazy val checkSnapshotDependenciesCommandKey = "release-check-snapshot-dependencies"
  lazy val checkSnapshotDependenciesCommand = Command.command(checkSnapshotDependenciesCommandKey)(checkSnapshotDependencies)
  lazy val checkSnapshotDependencies: ReleasePart = { st =>
    val extracted = Project.extract(st)
    val snapshotDeps = extracted.evalTask(snapshotDependencies, st)
    val useDefs = extracted.get(useDefaults)
    if (!snapshotDeps.isEmpty) {
      if (useDefs) {
        sys.error("Aborting release due to snapshot dependencies.")
      } else {
        st.logger.warn("Snapshot dependencies detected:\n" + snapshotDeps.mkString("\n"))
        SimpleReader.readLine("Do you want to continue (y/n)? [n] ") match {
          case Some("y") | Some("Y") =>
          case _ => sys.error("Aborting release due to snapshot dependencies.")
        }
      }
    }
    st
  }

  lazy val inquireVersionsCommandKey = "release-inquire-versions"
  lazy val inquireVersionsCommand = Command.command(inquireVersionsCommandKey)(inquireVersions)
  lazy val inquireVersions: ReleasePart = { st =>
    val extracted = Project.extract(st)
    val useDefs = extracted.get(useDefaults)
    val (releaseVersion, nextVersion) = extracted.get(versions)

    val releaseV =
      if (useDefs) releaseVersion
      else readVersion(releaseVersion, "Release version [%s] : " format releaseVersion)

    val nextV =
      if (useDefs) nextVersion
      else readVersion(nextVersion, "Next version [%s] : " format nextVersion)

    extracted.append(Seq(versions := (releaseV, nextV)), st)
  }

  lazy val setReleaseVersionCommandKey = "release-set-release-version"
  lazy val setReleaseVersionCommand = Command.command(setReleaseVersionCommandKey)(setReleaseVersion)
  lazy val setReleaseVersion:ReleasePart = setVersion(_._1)

  lazy val setNextVersionCommandKey = "release-set-next-version"
  lazy val setNextVersionCommand = Command.command(setNextVersionCommandKey)(setNextVersion)
  lazy val setNextVersion: ReleasePart = setVersion(_._2)


  def setVersion(selectVersion: Versions => String): ReleasePart =  { st =>
    val extracted = Project.extract(st)
    val vs = extracted.get(versions)
    val selected = selectVersion(vs)

    st.logger.info("Setting version to '%s'." format selected)


    val versionString = "%sversion in ThisBuild := \"%s\"%s" format (lineSep, selected, lineSep)
    IO.append(new File("version.sbt"), versionString)

    extracted.append(Seq(
      version := selected,
      versions := vs
    ), st)
  }

  lazy val commitReleaseVersionCommandKey = "release-commit-release-version"
  lazy val commitReleaseVersionCommand =  Command.command(commitReleaseVersionCommandKey)(commitReleaseVersion)
  lazy val commitReleaseVersion: ReleasePart = commitVersion("Releasing %s")

  lazy val commitNextVersionCommandKey = "release-commit-next-version"
  lazy val commitNextVersionCommand = Command.command(commitNextVersionCommandKey)(commitNextVersion)
  lazy val commitNextVersion: ReleasePart = commitVersion("Bump to %s")

  def commitVersion(msgPattern: String): ReleasePart = { st =>
    val extracted = Project.extract(st)
    val v = extracted.get(version)

    Git.add("version.sbt") !! st.logger
    Git.commit(msgPattern format v) !! st.logger

    st
  }

  lazy val tagReleaseCommandKey = "release-tag-release"
  lazy val tagReleaseCommand = Command.command(tagReleaseCommandKey)(tagRelease)
  lazy val tagRelease: ReleasePart = { st =>
    val extracted = Project.extract(st)
    val v = extracted.get(version)

    Git.tag(v) !! st.logger

    st
  }

  lazy val settings = Seq[Setting[_]](
    useDefaults := false,
  
    snapshotDependencies <<= (fullClasspath in Runtime) map { cp: Classpath =>
      val moduleIds = cp.flatMap(_.get(moduleID.key))
      val snapshots = moduleIds.filter(m => m.isChanging || m.revision.endsWith("-SNAPSHOT"))
      snapshots
    },

    versions <<= version apply (ver => Version(ver).map(
      v => (v.withoutQualifier.string, v.bump.asSnapshot.string)).getOrElse(versionFormatError)
    ),

    releaseProcess <<= thisProjectRef apply { ref =>
      Seq[ReleasePart](
        initialGitChecks,
        checkSnapshotDependencies,
        inquireVersions,
        releaseTask(test in Test in ref),
        setReleaseVersion,
        commitReleaseVersion,
        tagRelease,
        releaseTask(test in Test in ref),
        releaseTask(publishLocal in Global in ref),
        setNextVersion,
        commitNextVersion
      )
    },

    commands ++= Seq(
      releaseCommand,
      checkSnapshotDependenciesCommand,
      inquireVersionsCommand,
      setReleaseVersionCommand,
      setNextVersionCommand,
      initialGitChecksCommand,
      commitReleaseVersionCommand,
      commitNextVersionCommand,
      tagReleaseCommand)

  )

  def readVersion(ver: String, prompt: String): String = {
    SimpleReader.readLine(prompt) match {
      case Some("") => ver
      case Some(input) => Version(input).map(_.string).getOrElse(sys.error("Invalid version format!"))
      case None => sys.error("No version provided!")
    }
  }

  private def versionFormatError = sys.error("Version format is not compatible with [0-9]+([0-9]+)?([0-9]+)?(-.*)?")
}


object Utilities {
  val lineSep = sys.props.get("line.separator").getOrElse(sys.error("No line separator? Really?"))

  class StateW(st: State) {
    def logger = CommandSupport.logger(st)
  }
  implicit def stateW(st: State): StateW = new StateW(st)
}
