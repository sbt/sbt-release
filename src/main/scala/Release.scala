package sbtrelease

import sbt._
import Keys._
import complete.DefaultParsers._
import util.control.Exception._

object Release {

  lazy val snapshotDependencies = TaskKey[Seq[ModuleID]]("release-snapshot-dependencies")

  lazy val useDefaults = SettingKey[Boolean]("release-use-defaults")

  type Versions = (String, String)
  lazy val versions = SettingKey[Versions]("release-versions")
  lazy val inquireVersions = TaskKey[Versions]("release-inquire-versions")

  lazy val releaseStage2 = TaskKey[Unit]("release-stage-2")



  val releaseCommand = "release"
  val releaseParser = (Space ~> "with-defaults")?
  val release: Command = Command(releaseCommand)(_ => releaseParser) { (st, switch) =>
    val extracted = Project.extract(st)
    val ref = extracted.get(thisProjectRef)

    import Project.showFullKey

    Seq(
      checkSnapshotDependenciesCommand,
      showFullKey(inquireVersions in Global in ref),
      showFullKey(test in Test in ref),
      setReleaseVersionCommand,
      showFullKey(test in Test in ref),
      showFullKey(publishLocal in Global in ref),
      setNextVersionCommand
    ) ::: Project.extract(st).append(Seq(useDefaults := switch.isDefined), st)
  }

  val checkSnapshotDependenciesCommand = "release-check-snapshot-dependencies"
  val checkSnapshotDependencies = Command.command(checkSnapshotDependenciesCommand) { (st) =>
    val extracted = Project.extract(st)
    val (newSt, snapshotDeps) = extracted.runTask(snapshotDependencies, st)
    val useDefs = extracted.get(useDefaults)
    if (!snapshotDeps.isEmpty) {
      if (useDefs) {
        sys.error("Aborting release due to snapshot dependencies.")
      } else {
        CommandSupport.logger(newSt).warn("Snapshot dependencies detected:\n" + snapshotDeps.mkString("\n"))
        SimpleReader.readLine("Do you want to continue (y/n)? [n] ") match {
          case Some("y") | Some("Y") =>
          case _ => sys.error("Aborting release due to snapshot dependencies.")
        }
      }
    }
    newSt
  }

  val setReleaseVersionCommand = "release-set-release-version"
  val setReleaseVersion = setVersionCommand(setReleaseVersionCommand, _._1)

  val setNextVersionCommand = "release-set-next-version"
  val setNextVersion = setVersionCommand(setNextVersionCommand, _._2)


  def setVersionCommand(key: String, selectVersion: Versions => String) = Command.command(key) { st =>
    val extracted = Project.extract(st)
    val vs = extracted.get(versions)
    val selected = selectVersion(vs)

    CommandSupport.logger(st).info("Setting version to '%s'." format selected)

    extracted.append(Seq(
      version := selected,
      versions := vs
    ), st)
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

    inquireVersionsTask,

    commands ++= Seq(release, checkSnapshotDependencies, setReleaseVersion, setNextVersion)

  )

  def readVersion(ver: String, prompt: String): String = {
    SimpleReader.readLine(prompt) match {
      case Some("") => ver
      case Some(input) => Version(input).map(_.string).getOrElse(sys.error("Invalid version format!"))
      case None => sys.error("No version provided!")
    }
  }

  def inquireVersionsTask =
    inquireVersions <<= (useDefaults, versions, streams).map({ case (useDefs, (releaseVersion, nextVersion), s) =>
      val releaseV =
        if (useDefs) releaseVersion
        else readVersion(releaseVersion, "Release version [%s] : " format releaseVersion)

      val nextV =
        if (useDefs) nextVersion
        else readVersion(nextVersion, "Next version [%s] : " format nextVersion)

      (releaseV, nextV)
    }).updateState { case (st, vs) =>
      val extracted = Project.extract(st)
      extracted.append(Seq(versions := vs), st)
    }

  private def versionFormatError = sys.error("Version format is not compatible with [0-9]+([0-9]+)?([0-9]+)?(-.*)?")
}
