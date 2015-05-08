package sbtrelease

import sbt._
import Keys._
import complete.DefaultParsers._

object ReleasePlugin extends AutoPlugin {

  object autoImport {
    val releaseSnapshotDependencies = taskKey[Seq[ModuleID]]("Calculate the snapshot dependencies for a build")
    val releaseProcess = settingKey[Seq[ReleaseStep]]("The release process")
    val releaseVersion = settingKey[String => String]("The release version")
    val releaseNextVersion = settingKey[String => String]("The next release version")
    val releaseVersionBump = settingKey[Version.Bump]("How the version should be incremented")
    val releaseTagName = taskKey[String]("The name of the tag")
    val releaseTagComment = taskKey[String]("The comment to use when tagging")
    val releaseCommitMessage = taskKey[String]("The commit message to use when tagging")
    val releaseCrossBuild = settingKey[Boolean]("Whether the release should be cross built")
    val releaseVersionFile = settingKey[File]("The file to write the version to")
    val releaseUseGlobalVersion = settingKey[Boolean]("Whether to use a global version")

    val releaseVcs = settingKey[Option[Vcs]]("The VCS to use")
    val releasePublishArtifactsAction = taskKey[Unit]("The action that should be performed to publish artifacts")

    lazy val ReleaseTransformations = sbtrelease.ReleaseStateTransformations

    case class ReleaseStep(action: State => State, check: State => State = identity, enableCrossBuild: Boolean = false)

    object ReleaseStep {
      implicit def func2ReleasePart(f: State => State): ReleaseStep = ReleaseStep(f)

      implicit def releasePart2Func(rp: ReleaseStep): State=>State = rp.action
    }

    def releaseTask[T](key: TaskKey[T]) = { st: State =>
      Project.extract(st).runAggregated(key, st)
    }

    object ReleaseKeys {

      val versions = AttributeKey[Versions]("releaseVersions")
      val useDefaults = AttributeKey[Boolean]("releaseUseDefaults")
      val skipTests = AttributeKey[Boolean]("releaseSkipTests")
      val cross = AttributeKey[Boolean]("releaseCross")

      private lazy val releaseCommandKey = "release"
      private val WithDefaults = "with-defaults"
      private val SkipTests = "skip-tests"
      private val CrossBuild = "cross"
      private val FailureCommand = "--failure--"
      private val releaseParser = (Space ~> WithDefaults | Space ~> SkipTests | Space ~> CrossBuild).*

      val releaseCommand: Command = Command(releaseCommandKey)(_ => releaseParser) { (st, args) =>
        val extracted = Project.extract(st)
        val releaseParts = extracted.get(releaseProcess)
        val crossEnabled = extracted.get(releaseCrossBuild) || args.contains(CrossBuild)
        val startState = st
          .copy(onFailure = Some(FailureCommand))
          .put(useDefaults, args.contains(WithDefaults))
          .put(skipTests, args.contains(SkipTests))
          .put(cross, crossEnabled)

        val initialChecks = releaseParts.map(_.check)

        def filterFailure(f: State => State)(s: State): State = {
          s.remainingCommands match {
            case FailureCommand :: tail => s.fail
            case _ => f(s)
          }
        }

        val removeFailureCommand = { s: State =>
          s.remainingCommands match {
            case FailureCommand :: tail => s.copy(remainingCommands = tail)
            case _ => s
          }
        }

        val process = releaseParts.map { step =>
          if (step.enableCrossBuild && crossEnabled) {
            filterFailure(ReleaseStateTransformations.runCrossBuild(step.action)) _
          } else filterFailure(step.action) _
        }

        initialChecks.foreach(_(startState))
        Function.chain(process :+ removeFailureCommand)(startState)
      }
    }
  }

  import autoImport._
  import autoImport.ReleaseKeys._
  import ReleaseStateTransformations._

  override def trigger = allRequirements

  override def projectSettings = Seq[Setting[_]](
    releaseSnapshotDependencies := {
      val moduleIds = (managedClasspath in Runtime).value.flatMap(_.get(moduleID.key))
      val snapshots = moduleIds.filter(m => m.isChanging || m.revision.endsWith("-SNAPSHOT"))
      snapshots
    },

    releaseVersion := { ver => Version(ver).map(_.withoutQualifier.string).getOrElse(versionFormatError) },
    releaseVersionBump := Version.Bump.default,
    releaseNextVersion := {
      ver => Version(ver).map(_.bump(releaseVersionBump.value).asSnapshot.string).getOrElse(versionFormatError)
    },
    releaseUseGlobalVersion := true,
    releaseCrossBuild := false,

    releaseTagName := s"v${(version in ThisBuild).value}",
    releaseTagComment := s"Releasing ${(version in ThisBuild).value}",
    releaseCommitMessage := s"Setting version to ${(version in ThisBuild).value}",

    releaseVcs := Vcs.detect(baseDirectory.value),

    releaseVersionFile := file("version.sbt"),

    releasePublishArtifactsAction := publish.value,

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
