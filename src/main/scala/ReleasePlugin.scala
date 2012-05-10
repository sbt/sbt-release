package sbtrelease

import sbt._
import Keys._
import complete.DefaultParsers._



object ReleasePlugin extends Plugin {
  object ReleaseKeys {
    lazy val snapshotDependencies = TaskKey[Seq[ModuleID]]("release-snapshot-dependencies")
    lazy val releaseProcess = SettingKey[Seq[ReleasePart]]("release-process")
    lazy val releaseVersion = SettingKey[String => String]("release-release-version")
    lazy val nextVersion = SettingKey[String => String]("release-next-version")
    lazy val tagName = SettingKey[String]("release-tag-name")

    lazy val versions = AttributeKey[Versions]("release-versions")
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
    snapshotDependencies <<= (managedClasspath in Runtime) map { cp: Classpath =>
      val moduleIds = cp.flatMap(_.get(moduleID.key))
      val snapshots = moduleIds.filter(m => m.isChanging || m.revision.endsWith("-SNAPSHOT"))
      snapshots
    },

    releaseVersion := { ver => Version(ver).map(_.withoutQualifier.string).getOrElse(versionFormatError) },
    nextVersion := { ver => Version(ver).map(_.bumpMinor.asSnapshot.string).getOrElse(versionFormatError) },

    tagName <<= (version in ThisBuild) (v => "v" + v),

    releaseProcess <<= thisProjectRef apply { ref =>
      import ReleaseStateTransformations._
      Seq[ReleasePart](
        initialGitChecks,
        checkSnapshotDependencies,
        inquireVersions,
        runTest,
        setReleaseVersion,
        commitReleaseVersion,
        tagRelease,
        releaseTask(publish in Global in ref),
        setNextVersion,
        commitNextVersion,
        pushChanges
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
        tagReleaseCommand,
        pushChangesCommand
      )
    )
  }
}
