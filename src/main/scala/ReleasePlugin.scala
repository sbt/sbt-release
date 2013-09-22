package sbtrelease

import sbt._
import Keys._
import complete.DefaultParsers._

object ReleasePlugin extends Plugin {
  object ReleaseKeys {
    lazy val snapshotDependencies = TaskKey[Seq[ModuleID]]("release-snapshot-dependencies")
    lazy val releaseProcess = SettingKey[Seq[ReleaseStep]]("release-process")
    lazy val releaseVersion = SettingKey[String => String]("release-release-version")
    lazy val nextVersion = SettingKey[String => String]("release-next-version")
    lazy val versionBump = SettingKey[Version.Bump]("release-version-bump", "How the version should be incremented")
    lazy val tagName = TaskKey[String]("release-tag-name")
    lazy val tagComment = TaskKey[String]("release-tag-comment")
    lazy val commitMessage = TaskKey[String]("release-commit-message")
    lazy val crossBuild = SettingKey[Boolean]("release-cross-build")
    lazy val versionFile = SettingKey[File]("release-version-file")

    lazy val versionControlSystem = SettingKey[Option[Vcs]]("release-vcs")

    lazy val versions = AttributeKey[Versions]("release-versions")
    lazy val useDefaults = AttributeKey[Boolean]("release-use-defaults")
    lazy val skipTests = AttributeKey[Boolean]("release-skip-tests")
    lazy val cross = AttributeKey[Boolean]("release-cross")

    private lazy val releaseCommandKey = "release"
    private val WithDefaults = "with-defaults"
    private val SkipTests = "skip-tests"
    private val CrossBuild = "cross"
    private val releaseParser = (Space ~> WithDefaults | Space ~> SkipTests | Space ~> CrossBuild).*

    val releaseCommand: Command = Command(releaseCommandKey)(_ => releaseParser) { (st, args) =>
      val extracted = Project.extract(st)
      val releaseParts = extracted.get(releaseProcess)
      val crossEnabled = extracted.get(crossBuild) || args.contains(CrossBuild)
      val startState = st
        .put(useDefaults, args.contains(WithDefaults))
        .put(skipTests, args.contains(SkipTests))
        .put(cross, crossEnabled)

      val initialChecks = releaseParts.map(_.check)
      val process = releaseParts.map { step =>
        if (step.enableCrossBuild && crossEnabled) {
          ReleaseStateTransformations.runCrossBuild(step.action)
        } else step.action
      }

      initialChecks.foreach(_(startState))
      Function.chain(process)(startState)
    }
  }

  import ReleaseKeys._
  import ReleaseStateTransformations._

  lazy val releaseSettings = Seq[Setting[_]](
    snapshotDependencies <<= (managedClasspath in Runtime) map { cp: Classpath =>
      val moduleIds = cp.flatMap(_.get(moduleID.key))
      val snapshots = moduleIds.filter(m => m.isChanging || m.revision.endsWith("-SNAPSHOT"))
      snapshots
    },

    releaseVersion := { ver => Version(ver).map(_.withoutQualifier.string).getOrElse(versionFormatError) },
    versionBump := Version.Bump.default,
    nextVersion <<= (versionBump) { bumpType: Version.Bump =>
      ver => Version(ver).map(_.bump(bumpType).asSnapshot.string).getOrElse(versionFormatError)
    },
    crossBuild <<= (scalaVersion, crossScalaVersions) { (sv, csv) => (csv.toSet - sv).nonEmpty },

    tagName <<= (version in ThisBuild) map (v => "v" + v),
    tagComment <<= (version in ThisBuild) map (v => "Releasing %s" format v),
    commitMessage <<= (version in ThisBuild) map (v => "Setting version to %s" format v),

    versionControlSystem <<= (baseDirectory)(Vcs.detect(_)),

    versionFile := file("version.sbt"),

    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      setNextVersion,
      commitNextVersion,
      pushChanges
    ),

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
        initialVcsChecksCommand,
        commitReleaseVersionCommand,
        commitNextVersionCommand,
        tagReleaseCommand,
        pushChangesCommand
      )
    )
  }
}


case class ReleaseStep(action: State => State, check: State => State = identity, enableCrossBuild: Boolean = false)

object ReleaseStep {
  implicit def func2ReleasePart(f: State => State): ReleaseStep = ReleaseStep(f)

  implicit def releasePart2Func(rp: ReleaseStep): State=>State = rp.action
}
