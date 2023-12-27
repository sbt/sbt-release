package sbtrelease

import java.io.Serializable

import sbt._
import Keys._
import sbt.complete.DefaultParsers._
import sbt.complete.Parser

object ReleasePlugin extends AutoPlugin {

  object autoImport {
    val releaseSnapshotDependencies = taskKey[Seq[ModuleID]]("Calculate the snapshot dependencies for a build")
    val releaseProcess = settingKey[Seq[ReleaseStep]]("The release process")
    val releaseVersion = taskKey[String => String]("The release version")
    val releaseNextVersion = taskKey[String => String]("The next release version")
    val releaseVersionBump = taskKey[Version.Bump]("How the version should be incremented")
    val releaseTagName = taskKey[String]("The name of the tag")
    val releaseTagComment = taskKey[String]("The comment to use when tagging")
    val releaseCommitMessage = taskKey[String]("The commit message to use when tagging")
    val releaseNextCommitMessage = taskKey[String]("The commit message to use for next iteration")
    val releaseCrossBuild = settingKey[Boolean]("Whether the release should be cross built")
    val releaseVersionFile = settingKey[File]("The file to write the version to")
    val releaseUseGlobalVersion = settingKey[Boolean]("Whether to use a global version")
    val releaseIgnoreUntrackedFiles = settingKey[Boolean]("Whether to ignore untracked files")
    val releaseVcsSign = settingKey[Boolean]("Whether to sign VCS commits and tags")
    val releaseVcsSignOff = settingKey[Boolean]("Whether to signoff VCS commits")

    val releaseVcs = settingKey[Option[Vcs]]("The VCS to use")
    val releasePublishArtifactsAction = taskKey[Unit]("The action that should be performed to publish artifacts")

    lazy val ReleaseTransformations = sbtrelease.ReleaseStateTransformations

    case class ReleaseStep(action: State => State, check: State => State = identity, enableCrossBuild: Boolean = false)

    object ReleaseStep {
      implicit def func2ReleasePart(f: State => State): ReleaseStep = ReleaseStep(f)

      implicit def releasePart2Func(rp: ReleaseStep): State=>State = rp.action
    }

    @deprecated("Use releaseStepTaskAggregated", "1.0.0")
    def releaseTask[T](key: TaskKey[T]) = { st: State =>
      Project.extract(st).runAggregated(key, st)
    }

    /**
     * Convert the given task key to a release step action.
     */
    def releaseStepTask[T](key: TaskKey[T]) = { st: State =>
      Project.extract(st).runTask(key, st)._1
    }

    /**
     * Convert the given task key to a release step action that gets run aggregated.
     */
    def releaseStepTaskAggregated[T](key: TaskKey[T]): State => State = { st: State =>
      Project.extract(st).runAggregated(key, st)
    }

    /**
     * Convert the given input task key and input to a release step action.
     */
    def releaseStepInputTask[T](key: InputKey[T], input: String = ""): State => State = { st: State =>
      import EvaluateTask._
      val extracted = Project.extract(st)
      val inputTask = extracted.get(Scoped.scopedSetting(key.scope, key.key))
      val task = Parser.parse(input, inputTask.parser(st)) match {
        case Right(t) => t
        case Left(msg) => sys.error(s"Invalid programmatic input:\n$msg")
      }
      val config = extractedTaskConfig(extracted, extracted.structure, st)
      withStreams(extracted.structure, st) { str =>
        val nv = nodeView(st, str, key :: Nil)
        val (newS, result) = runTask(task, st, str, extracted.structure.index.triggers, config)(nv)
        (newS, processResult(result, newS.log))
      }._1
    }

    /**
     * Convert the given command and input to a release step action
     */
    def releaseStepCommand(command: Command, input: String = ""): State => State = { st: State =>
      Parser.parse(input, command.parser(st)) match {
        case Right(cmd) => cmd()
        case Left(msg) => sys.error(s"Invalid programmatic input:\n$msg")
      }
    }

    /**
     * Convert the given command string to a release step action
     */
    def releaseStepCommand(command: String): State => State = { st: State =>
      Parser.parse(command, st.combinedParser) match {
        case Right(cmd) => cmd()
        case Left(msg) => sys.error(s"Invalid programmatic input:\n$msg")
      }
    }

    /**
     * Convert the given command string to a release step action, preserving and invoking remaining commands
     */
    def releaseStepCommandAndRemaining(command: String): State => State = { initState: State =>
      import Compat._
      @annotation.tailrec
      def runCommand(command: Compat.Command, state: State): State = {
        val nextState = Parser.parse(command, state.combinedParser) match {
          case Right(cmd) => cmd()
          case Left(msg) => sys.error(s"Invalid programmatic input:\n$msg")
        }
        nextState.remainingCommands.toList match {
          case Nil => nextState.copy(remainingCommands = initState.remainingCommands)
          case Compat.FailureCommand :: tail => nextState.copy(remainingCommands = FailureCommand +: initState.remainingCommands)
          case head :: tail => runCommand(head, nextState.copy(remainingCommands = tail))
        }
      }
      runCommand(command, initState.copy(remainingCommands = Nil))
    }

    object ReleaseKeys {

      val versions = AttributeKey[Versions]("releaseVersions")
      val commandLineReleaseVersion = AttributeKey[Option[String]]("release-input-release-version")
      val commandLineNextVersion = AttributeKey[Option[String]]("release-input-next-version")
      val useDefaults = AttributeKey[Boolean]("releaseUseDefaults")
      val skipTests = AttributeKey[Boolean]("releaseSkipTests")
      val cross = AttributeKey[Boolean]("releaseCross")
      val tagDefault = AttributeKey[Option[String]]("release-default-tag-exists-answer")

      private lazy val releaseCommandKey = "release"
      private val FailureCommand = Compat.FailureCommand

      private[this] val WithDefaults: Parser[ParseResult] =
        (Space ~> token("with-defaults")) ^^^ ParseResult.WithDefaults
      private[this] val SkipTests: Parser[ParseResult] =
        (Space ~> token("skip-tests")) ^^^ ParseResult.SkipTests
      private[this] val CrossBuild: Parser[ParseResult] =
        (Space ~> token("cross")) ^^^ ParseResult.CrossBuild
      private[this] val ReleaseVersion: Parser[ParseResult] =
        (Space ~> token("release-version") ~> Space ~> token(StringBasic, "<release version>")) map ParseResult.ReleaseVersion
      private[this] val NextVersion: Parser[ParseResult] =
        (Space ~> token("next-version") ~> Space ~> token(StringBasic, "<next version>")) map ParseResult.NextVersion
      private[this] val TagDefault: Parser[ParseResult] =
        (Space ~> token("default-tag-exists-answer") ~> Space ~> token(StringBasic, "o|k|a|<tag-name>")) map ParseResult.TagDefault


      private[this] sealed abstract class ParseResult extends Product with Serializable
      private[this] object ParseResult {
        final case class ReleaseVersion(value: String) extends ParseResult
        final case class NextVersion(value: String) extends ParseResult
        final case class TagDefault(value: String) extends ParseResult
        case object WithDefaults extends ParseResult
        case object SkipTests extends ParseResult
        case object CrossBuild extends ParseResult
      }

      private[this] val releaseParser: Parser[Seq[ParseResult]] = (ReleaseVersion | NextVersion | WithDefaults | SkipTests | CrossBuild | TagDefault).*

      val releaseCommand: Command = Command(releaseCommandKey)(_ => releaseParser) { (st, args) =>
        val extracted = Project.extract(st)
        val releaseParts = extracted.get(releaseProcess)
        val crossEnabled = extracted.get(releaseCrossBuild) || args.contains(ParseResult.CrossBuild)

        val startState = st
          .copy(onFailure = Some(FailureCommand))
          .put(useDefaults, args.contains(ParseResult.WithDefaults))
          .put(skipTests, args.contains(ParseResult.SkipTests))
          .put(cross, crossEnabled)
          .put(tagDefault, args.collectFirst{case ParseResult.TagDefault(value) => value})
          .put(commandLineReleaseVersion, args.collectFirst{case ParseResult.ReleaseVersion(value) => value})
          .put(commandLineNextVersion, args.collectFirst{case ParseResult.NextVersion(value) => value})

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

        val failureCheck = { s: State =>
          filterFailure(_.copy(onFailure = Some(FailureCommand)))(s)
        }

        val process = releaseParts.map { step =>
          if (step.enableCrossBuild && crossEnabled) {
            filterFailure(ReleaseStateTransformations.runCrossBuild(step.action)) _
          } else filterFailure(step.action) _
        }

        initialChecks.foreach(_(startState))
        Function.chain(
          (process :+ removeFailureCommand).flatMap(Seq(_, failureCheck))
        )(startState)
      }
    }
  }

  import autoImport._
  import autoImport.ReleaseKeys._
  import ReleaseStateTransformations._

  override def trigger = allRequirements

  val runtimeVersion = Def.task {
    val v1 = (ThisBuild / version).value
    val v2 = version.value
    if (releaseUseGlobalVersion.value) v1 else v2
  }

  override def projectSettings = Seq[Setting[_]](
    releaseSnapshotDependencies := {
      val moduleIds = (Runtime / managedClasspath).value.flatMap(_.get(moduleID.key))
      val snapshots = moduleIds.filter(m => m.isChanging || m.revision.endsWith("-SNAPSHOT"))
      snapshots
    },

    releaseVersion := { ver => Version(ver).map(_.withoutQualifier.string).getOrElse(versionFormatError(ver)) },
    releaseVersionBump := Version.Bump.default,
    releaseNextVersion := {
      ver => Version(ver).map(_.bump(releaseVersionBump.value).asSnapshot.string).getOrElse(versionFormatError(ver))
    },
    releaseUseGlobalVersion := true,
    releaseCrossBuild := false,

    releaseTagName := s"v${runtimeVersion.value}",
    releaseTagComment := s"Releasing ${runtimeVersion.value}",

    releaseCommitMessage := s"Setting version to ${runtimeVersion.value}",
    releaseNextCommitMessage := s"Setting version to ${runtimeVersion.value}",

    releaseVcs := Vcs.detect(baseDirectory.value),
    releaseVcsSign := false,
    releaseVcsSignOff := false,

    releaseVersionFile := baseDirectory.value / "version.sbt",

    releasePublishArtifactsAction := publish.value,

    releaseIgnoreUntrackedFiles := false,

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
