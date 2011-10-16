package sbtrelease

import sbt._
import Keys._
import complete.DefaultParsers._
import util.control.Exception._

object Release {
  import Utilities._

  lazy val snapshotDependencies = TaskKey[Seq[ModuleID]]("release-snapshot-dependencies")

  lazy val useDefaults = SettingKey[Boolean]("release-use-defaults")

  type Versions = (String, String)
  lazy val versions = SettingKey[Versions]("release-versions")


  val releaseCommand = "release"
  val releaseParser = (Space ~> "with-defaults")?
  val release: Command = Command(releaseCommand)(_ => releaseParser) { (st, switch) =>
    val extracted = Project.extract(st)
    val ref = extracted.get(thisProjectRef)

    import Project.showFullKey

    Seq(
      initialGitChecksCommand,
      checkSnapshotDependenciesCommand,
      inquireVersionsCommand,
      showFullKey(test in Test in ref),
      setReleaseVersionCommand,
      commitReleaseVersionCommand,
      tagReleaseCommand,
      showFullKey(test in Test in ref),
      showFullKey(publishLocal in Global in ref),
      setNextVersionCommand,
      commitNextVersionCommand
    ) ::: Project.extract(st).append(Seq(useDefaults := switch.isDefined), st)
  }

  val initialGitChecksCommand = "release-git-checks"
  val initialGitChecks = Command.command(initialGitChecksCommand) { st =>
    if (!new File(".git").exists) {
      sys.error("Aborting release. Working directory is not a git repository.")
    }
    val status = (Git.status !!).trim
    if (!status.isEmpty) {
      sys.error("Aborting release. Working directory is dirty.")
    }
    st
  }

  val checkSnapshotDependenciesCommand = "release-check-snapshot-dependencies"
  val checkSnapshotDependencies = Command.command(checkSnapshotDependenciesCommand) { (st) =>
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

  val inquireVersionsCommand = "release-inquire-versions"
  val inquireVersions = Command.command(inquireVersionsCommand) { st =>
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

  val setReleaseVersionCommand = "release-set-release-version"
  val setReleaseVersion = setVersionCommand(setReleaseVersionCommand, _._1)

  val setNextVersionCommand = "release-set-next-version"
  val setNextVersion = setVersionCommand(setNextVersionCommand, _._2)


  def setVersionCommand(key: String, selectVersion: Versions => String) = Command.command(key) { st =>
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

  val commitReleaseVersionCommand = "release-commit-release-version"
  val commitReleaseVersion = commitVersion(commitReleaseVersionCommand, "Releasing %s")

  val commitNextVersionCommand = "release-commit-next-version"
  val commitNextVersion = commitVersion(commitNextVersionCommand, "Bump to %s")

  def commitVersion(key: String, msgPattern: String) = Command.command(key) { st =>
    val extracted = Project.extract(st)
    val v = extracted.get(version)

    Git.add("version.sbt") !! st.logger
    Git.commit(msgPattern format v) !! st.logger

    st
  }

  val tagReleaseCommand = "release-tag-release"
  val tagRelease = Command.command(tagReleaseCommand) { st =>
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

    commands ++= Seq(
      release,
      checkSnapshotDependencies,
      inquireVersions,
      setReleaseVersion,
      setNextVersion,
      initialGitChecks,
      commitReleaseVersion,
      commitNextVersion,
      tagRelease)

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
