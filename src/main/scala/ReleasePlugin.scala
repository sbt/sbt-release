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
    lazy val tagPrefix = SettingKey[String]("tag-prefix", "Allow to prefix tag-name, tag-comment and commit-message at once")
    lazy val tagName = TaskKey[String]("release-tag-name")
    lazy val tagComment = TaskKey[String]("release-tag-comment")
    lazy val commitMessage = TaskKey[String]("release-commit-message")

    // a non-global release will generate version.sbt file containing a version scoped to the current project, not to the build
    lazy val globalRelease = SettingKey[Boolean]("global-release", "Release the current project and all its aggregated projects as one global project under " +
      "a common version (i.e a version scoped to the build), otherwise release only the current project allowing a different version scheme than its parent " +
      "(i.e a version scoped to the project only)")
    lazy val interactiveCommit = SettingKey[Boolean]("release-interactive-commit", "If the repository is dirty, allow the user to commit within the SBT shell")

    lazy val versionControlSystem = SettingKey[Option[Vcs]]("release-vcs")

    lazy val versions = AttributeKey[Versions]("release-versions")
    lazy val useDefaults = AttributeKey[Boolean]("release-use-defaults")
    lazy val skipTests = AttributeKey[Boolean]("release-skip-tests")
    lazy val crossBuild = AttributeKey[Boolean]("release-cross-build")

    private lazy val releaseCommandKey = "release"
    private val WithDefaults = "with-defaults"
    private val SkipTests = "skip-tests"
    private val CrossBuild = "cross"
    private val releaseParser = (Space ~> WithDefaults | Space ~> SkipTests | Space ~> CrossBuild).*

    val releaseCommand: Command = Command(releaseCommandKey)(_ => releaseParser) { (st, args) =>
      val extracted = Project.extract(st)
      val releaseParts = extracted.get(releaseProcess)
      val crossEnabled = args.contains(CrossBuild)
      val startState = st
        .put(useDefaults, args.contains(WithDefaults))
        .put(skipTests, args.contains(SkipTests))
        .put(crossBuild, crossEnabled)

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

    interactiveCommit := true,
    globalRelease := true,

    releaseVersion := { ver => Version(ver).map(_.withoutQualifier.string).getOrElse(versionFormatError) },
    nextVersion := { ver => Version(ver).map(_.bumpMinor.asSnapshot.string).getOrElse(versionFormatError) },

    tagPrefix := "",
    tagName <<= (version, tagPrefix) map {(v,pref) => pref + "v" + v},
    tagComment <<= (version, tagPrefix) map ((v,pref) => "Releasing %s%s" format (pref,v)),
    commitMessage <<= (version, tagPrefix) map ((v,pref) => "Setting version to %s%s" format (pref,v)),

    versionControlSystem <<= (baseDirectory)(Vcs.detect(_)),

    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
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
