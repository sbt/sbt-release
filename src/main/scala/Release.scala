package sbtrelease

import sbt._
import Keys._
import util.control.Exception._

object Release {

  lazy val snapshotDependencies = TaskKey[Seq[ModuleID]]("release-snapshot-dependencies")
  lazy val checkSnapshotDependencies = TaskKey[Boolean]("release-check-snapshot-dependencies")

  type Versions = (String, String)
  lazy val versions = SettingKey[Versions]("release-versions")
  lazy val inquireVersions = TaskKey[Versions]("release-inquire-versions")

  lazy val useDefaults = TaskKey[Boolean]("release-use-defaults")

  lazy val release = TaskKey[Unit]("release")
  lazy val releaseStage2 = TaskKey[Unit]("release-stage-2")



  lazy val settings = Seq[Setting[_]](

    snapshotDependencies <<= (fullClasspath in Runtime) map { cp: Classpath =>
      val moduleIds = cp.flatMap(_.get(moduleID.key))
      val snapshots = moduleIds.filter(m => m.isChanging || m.revision.endsWith("-SNAPSHOT"))
      snapshots
    },

    checkSnapshotDependencies <<= (snapshotDependencies, streams) map ( (snapshots, s) =>
      if (!snapshots.isEmpty) {
        s.log.warn("Snapshot dependencies detected:" + snapshots.mkString("\n"))
        SimpleReader.readLine("Do you want to continue (y/n)? [n] ") match {
          case Some("y") | Some("Y") => true
          case _ => false
        }
      } else
        true
      ),

    loadOrSetVersions,
    inquireVersionsTask,

    release <<= (checkSnapshotDependencies, inquireVersions.task) flatMap {
      (continueWithSnapshotDeps, inquire) =>
        if (continueWithSnapshotDeps) inquire map ( _ => ())
        else sys.error("Aborting release with snapshot dependencies!")
    },

    // executes the tests, set the version to the release-version and let release2 run after reapplying the settings.
    release <<= (release, executeTests.task in Test) flatMap {
      (_, testTask) => testTask.map( _ => ())
    } updateState { case (st, _) =>
      val extracted = Project.extract(st)
      val (releaseV, nextV) = extracted.get(versions)
      releaseStage2.key.label :: extracted.append(Seq(
        version := releaseV,
        versions := (releaseV, nextV)
      ), st)
    },

    // releaseStage2 is run after the version has been set to the release-version in the release task.
    // using publish-local for now.
    releaseStage2 <<= (test in Test, publishLocal.task) flatMap {
      (_, publishTask) => publishTask
    } updateState { case (st, _) =>
      val extracted = Project.extract(st)
      val (_, nextV) = extracted.get(versions)
      extracted.append(Seq(version := nextV), st)
    }

  )

  def readVersion(ver: String, prompt: String): String = {
    SimpleReader.readLine(prompt) match {
      case Some("") => ver
      case Some(input) => Version(input).map(_.string).getOrElse(sys.error("Invalid version format!"))
      case None => sys.error("No version provided!")
    }
  }

  def inquireVersionsTask =
    inquireVersions <<= (versions, streams).map({ case ((releaseVersion, nextVersion), s) =>
      val releaseV = readVersion(releaseVersion, "Release version [%s] : " format releaseVersion)
      val nextV = readVersion(nextVersion, "Next version [%s] : " format nextVersion)
      (releaseV, nextV)
    }).updateState { case (st, vs) =>
      val extracted = Project.extract(st)
      extracted.append(Seq(versions := vs), st)
    }

  def loadOrSetVersions = versions <<= version(ver => Version(ver).map(
    v => (v.withoutQualifier.string, v.bump.asSnapshot.string)).getOrElse(versionFormatError)
  )

  private def versionFormatError = sys.error("Version format is not compatible with [0-9]+([0-9]+)?([0-9]+)?(-.*)?")

  def clearKey[T](key: ScopedKey[Task[T]], state: State): State = {
    import SessionVar._
    state.update(sessionVars)(om => Map(orEmpty(om).map.remove (Key(key))))
  }

}
