package sbtrelease

import sbt._
import Keys._
import complete.DefaultParsers._

object Release {

  object ReleaseKeys {
    lazy val snapshotDependencies = TaskKey[Seq[ModuleID]]("release-snapshot-dependencies")
    lazy val useDefaults = SettingKey[Boolean]("release-use-defaults")
    lazy val versions = SettingKey[Versions]("release-versions")
    lazy val releaseProcess = SettingKey[Seq[ReleasePart]]("release-process")

    private lazy val releaseCommandKey = "release"
    private val releaseParser = (Space ~> "with-defaults")?

    val releaseCommand: Command = Command(releaseCommandKey)(_ => releaseParser) { (st, switch) =>
      val extracted = Project.extract(st)
      val process = extracted.get(releaseProcess)

      val startState = extracted.append(Seq(useDefaults := switch.isDefined), st)

      Function.chain(process)(startState)
    }
  }

  import ReleaseKeys._

  lazy val releaseSettings = Seq[Setting[_]](
    useDefaults := false,

    snapshotDependencies <<= (fullClasspath in Runtime) map { cp: Classpath =>
      val moduleIds = cp.flatMap(_.get(moduleID.key))
      val snapshots = moduleIds.filter(m => m.isChanging || m.revision.endsWith("-SNAPSHOT"))
      snapshots
    },

    versions <<= version apply { ver =>
      Version(ver).map {
        v => (v.withoutQualifier.string, v.bump.asSnapshot.string)
      } getOrElse(versionFormatError)
    },

    releaseProcess <<= thisProjectRef apply { ref =>
      import ReleaseStateTransformations._
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

    commands += releaseCommand
  )

  lazy val extraReleaseCommands = {
    import ExtraReleaseCommands._

    Seq[Setting[_]](
      commands ++= Seq(
        checkSnapshotDependenciesCommand,
        inquireVersionsCommand,
        setReleaseVersionCommand,
        setNextVersionCommand,
        initialGitChecksCommand,
        commitReleaseVersionCommand,
        commitNextVersionCommand,
        tagReleaseCommand
      )
    )
  }
}
