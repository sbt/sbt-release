package sbtrelease

import sbt._
import Keys._
import util.control.Exception._

object Release {

  lazy val snapshotDependencies = TaskKey[Seq[ModuleID]]("release-snapshot-dependencies")
  lazy val checkSnapshotDependencies = TaskKey[Boolean]("release-check-snapshot-dependencies")

  type Versions = (String, String)
  lazy val versions = TaskKey[Versions]("release-versions")
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
      val (newSt, (releaseV, _)) = extracted.runTask(versions, st)
      releaseStage2.key.label :: extracted.append(Seq(
        version := releaseV
      ), newSt)
    },

    releaseStage2 <<= (test in Test, publishLocal.task) flatMap {
      (_, publishTask) => publishTask
    } updateState { case (st, _) =>
      val extracted = Project.extract(st)
      val (newSt, (_, nextV)) = extracted.runTask(versions, st)
      extracted.append(Seq(version := nextV), newSt)
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
    }).keepAs(versions)

  def loadOrSetVersions = versions <<= {
    val init: Project.Initialize[Task[Versions]] = (state, resolvedScoped, version) map ((s, ctx, ver) =>
      getFromContext(versions, ctx, s) match {
        case Some(v) => v
        case None => Version(ver).map( v => (v.withoutQualifier.string, v.bump.asSnapshot.string)).getOrElse(versionFormatError)
      })
    init.keepAs(versions)
  }

  private def versionFormatError = sys.error("Version format is not compatible with [0-9]+([0-9]+)?([0-9]+)?(-.*)?")

  def clearKey[T](key: ScopedKey[Task[T]], state: State): State = {
    import SessionVar._
    state.update(sessionVars)(om => Map(orEmpty(om).map.remove (Key(key))))
  }

}


private[sbtrelease] object Version {
  val VersionR = """([0-9]+)(?:(?:\.([0-9]+))?(?:\.([0-9]+))?)?(-.*)?""".r

  def apply(s: String): Option[Version] = {
    allCatch opt {
      val VersionR(maj, min, mic, qual) = s
      Version(maj.toInt, Option(min).map(_.toInt), Option(mic).map(_.toInt), Option(qual))
    }
  }
}

private[sbtrelease] case class Version(major: Int, minor: Option[Int], micro: Option[Int], qualifier: Option[String]) {
  def bump = {
    val maybeBumpedMicro = micro.map(m => copy(micro = Some(m + 1)))
    val maybeBumpedMinor = minor.map(m => copy(minor = Some(m + 1)))
    lazy val bumpedMajor = copy(major = major + 1)

    maybeBumpedMicro.orElse(maybeBumpedMinor).getOrElse(bumpedMajor)
  }

  def withoutQualifier = copy(qualifier = None)
  def asSnapshot = copy(qualifier = Some("-SNAPSHOT"))

  def string = "" + major + get(minor) + get(micro) + qualifier.getOrElse("")

  private def get(part: Option[Int]) = part.map("." + _).getOrElse("")
}
