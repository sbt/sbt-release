package sbtrelease

import sbt._
import Keys._
import complete.DefaultParsers._

object Release {

  object ReleaseKeys {
    lazy val snapshotDependencies = TaskKey[Seq[ModuleID]]("release-snapshot-dependencies")
    lazy val versions = SettingKey[Versions]("release-versions")
    lazy val releaseProcess = SettingKey[Seq[ReleasePart]]("release-process")

    lazy val useDefaults = AttributeKey[Boolean]("release-use-defaults")
    lazy val skipTests = AttributeKey[Boolean]("release-skip-tests")

    private lazy val releaseCommandKey = "release"
    private val WithDefaults = "with-defaults"
    private val SkipTests = "skip-tests"
    private val releaseParser = (Space ~> WithDefaults | Space ~> SkipTests).*

    val releaseCommand: Command = Command(releaseCommandKey)(_ => releaseParser) { (st, args) =>
      val extracted = Project.extract(st)
      val process = extracted.get(releaseProcess)

      val startState = st
        .put(useDefaults, args.contains(WithDefaults))
        .put(skipTests, args.contains(SkipTests))

      Function.chain(process)(startState)
    }
  }

  import ReleaseKeys._

  lazy val releaseSettings = Seq[Setting[_]](
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
        runTest,
        setReleaseVersion,
        runTest,
        releaseTask(publishLocal in Global in ref),
        commitReleaseVersion,
        tagRelease,
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
