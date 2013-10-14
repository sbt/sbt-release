package sbtrelease

import java.io.File
import sbt._
import Keys._
import annotation.tailrec
import sbtrelease.ReleasePlugin.ReleaseKeys._
import sbt.Package.ManifestAttributes
import scala.Some
import sbt.Value
import sbt.Inc
import sbt.Extracted

object ReleaseStateTransformations {
  import ReleasePlugin.ReleaseKeys._
  import Utilities._

  lazy val checkSnapshotDependencies: ReleaseStep = { st: State =>
    val thisRef = st.extract.get(thisProjectRef)
    val (newSt, result) = SbtCompat.runTaskAggregated(snapshotDependencies in thisRef, st)
    val snapshotDeps = result match {
      case Value(value) => value.flatMap(_.value)
      case Inc(cause) => sys.error("Error checking for snapshot dependencies: " + cause)
    }
    if (!snapshotDeps.isEmpty) {
      val useDefaults = extractDefault(newSt, "n")
      st.log.warn("Snapshot dependencies detected:\n" + snapshotDeps.mkString("\n"))
      useDefaults orElse SimpleReader.readLine("Do you want to continue (y/n)? [n] ") match {
        case Yes() =>
        case _ => sys.error("Aborting release due to snapshot dependencies.")
      }
    }
    newSt
  }


  lazy val inquireVersions: ReleaseStep = { st: State =>
    val extracted = Project.extract(st)

    val useDefs = st.get(useDefaults).getOrElse(false)
    val currentV = extracted.get(version)

    val releaseFunc = extracted.get(releaseVersion)
    val suggestedReleaseV = releaseFunc(currentV)

    val releaseV = readVersion(suggestedReleaseV, "Release version [%s] : ", useDefs)

    val nextFunc = extracted.get(nextVersion)
    val suggestedNextV = nextFunc(releaseV)
    val nextV = readVersion(suggestedNextV, "Next version [%s] : ", useDefs)

    st.put(versions, (releaseV, nextV))
  }


  lazy val runClean : ReleaseStep = ReleaseStep(
    action = { st: State =>
      val extracted = Project.extract(st)
      val ref = extracted.get(thisProjectRef)
      extracted.runAggregated(clean in Global in ref, st)
    }
  )


  lazy val runTest: ReleaseStep = ReleaseStep(
    action = { st: State =>
      if (!st.get(skipTests).getOrElse(false)) {
        val extracted = Project.extract(st)
        val ref = extracted.get(thisProjectRef)
        extracted.runAggregated(test in Test in ref, st)
      } else st
    },
    enableCrossBuild = true
  )

  lazy val setReleaseVersion: ReleaseStep = setVersion(_._1)
  lazy val setNextVersion: ReleaseStep = setVersion(_._2)
  private[sbtrelease] def setVersion(selectVersion: Versions => String): ReleaseStep =  { st: State =>
    val vs = st.get(versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
    val selected = selectVersion(vs)

    st.log.info("Setting version to '%s'." format selected)

    val versionString = "version in ThisBuild := \"%s\"" format selected
    writeVersion(st, versionString)

    reapply(Seq(
      version in ThisBuild := selected
    ), st)
  }

  private def vcs(st: State): Vcs = {
    st.extract.get(versionControlSystem).getOrElse(sys.error("Aborting release. Working directory is not a repository of a recognized VCS."))
  }

  private def writeVersion(st: State, versionString: String) {
    val file = st.extract.get(versionFile)
    IO.write(file, versionString)
  }

  private[sbtrelease] lazy val initialVcsChecks = { st: State =>
    val status = (vcs(st).status !!).trim
    if (status.nonEmpty) {
      sys.error("Aborting release. Working directory is dirty.")
    }

    st.log.info("Starting release process off commit: " + vcs(st).currentHash)
    st
  }

  lazy val commitReleaseVersion = ReleaseStep(commitReleaseVersionAction, initialVcsChecks)
  private[sbtrelease] lazy val commitReleaseVersionAction = { st: State =>
    val newState = commitVersion(st)
    reapply(Seq[Setting[_]](
      packageOptions += ManifestAttributes(
        "Vcs-Release-Hash" -> vcs(st).currentHash
      )
    ), newState)
  }

  lazy val commitNextVersion = ReleaseStep(commitVersion)
  private[sbtrelease] def commitVersion = { st: State =>
    val file = st.extract.get(versionFile)
    val base = vcs(st).baseDir
    val relativePath = IO.relativize(base, file).getOrElse("Version file [%s] is outside of this VCS repository with base directory [%s]!" format(file, base))

    vcs(st).add(relativePath) !! st.log
    val status = (vcs(st).status !!) trim

    val newState = if (status.nonEmpty) {
      val (state, msg) = st.extract.runTask(commitMessage, st)
      vcs(state).commit(msg) ! st.log
      state
    } else {
      // nothing to commit. this happens if the version.sbt file hasn't changed.
      st
    }
    newState
  }

  lazy val tagRelease: ReleaseStep = { st: State =>
    val defaultChoice = extractDefault(st, "a")

    @tailrec
    def findTag(tag: String): Option[String] = {
      if (vcs(st).existsTag(tag)) {
        defaultChoice orElse SimpleReader.readLine("Tag [%s] exists! Overwrite, keep or abort or enter a new tag (o/k/a)? [a] " format tag) match {
          case Some("" | "a" | "A") =>
            sys.error("Aborting release!")

          case Some("k" | "K") =>
            st.log.warn("The current tag [%s] does not point to the commit for this release!" format tag)
            None

          case Some("o" | "O") =>
            st.log.warn("Overwriting a tag can cause problems if others have already seen the tag (see `%s help tag`)!" format vcs(st).commandName)
            Some(tag)

          case Some(newTag) =>
            findTag(newTag)

          case None =>
            sys.error("No tag entered. Aborting release!")
        }
      } else {
        Some(tag)
      }
    }

    val (tagState, tag) = st.extract.runTask(tagName, st)
    val (commentState, comment) = st.extract.runTask(tagComment, tagState)
    val tagToUse = findTag(tag)
    tagToUse.foreach(vcs(commentState).tag(_, comment, force = true) !! commentState.log)


    tagToUse map (t =>
      reapply(Seq[Setting[_]](
        packageOptions += ManifestAttributes("Vcs-Release-Tag" -> t)
      ), commentState)
    ) getOrElse commentState
  }

  lazy val pushChanges: ReleaseStep = ReleaseStep(pushChangesAction, checkUpstream)
  private[sbtrelease] lazy val checkUpstream = { st: State =>
    if (!vcs(st).hasUpstream) {
      sys.error("No tracking branch is set up. Either configure a remote tracking branch, or remove the pushChanges release part.")
    }
    val defaultChoice = extractDefault(st, "n")

    st.log.info("Checking remote [%s] ..." format vcs(st).trackingRemote)
    if (vcs(st).checkRemote(vcs(st).trackingRemote) ! st.log != 0) {
      defaultChoice orElse SimpleReader.readLine("Error while checking remote. Still continue (y/n)? [n] ") match {
        case Yes() => // do nothing
        case _ => sys.error("Aborting the release!")
      }
    }

    if (vcs(st).isBehindRemote) {
      defaultChoice orElse SimpleReader.readLine("The upstream branch has unmerged commits. A subsequent push will fail! Continue (y/n)? [n] ") match {
        case Yes() => // do nothing
        case _ => sys.error("Merge the upstream commits and run `release` again.")
      }
    }
    st
  }

  private[sbtrelease] lazy val pushChangesAction = { st: State =>
    val defaultChoice = extractDefault(st, "y")

    val vc = vcs(st)
    if (vc.hasUpstream) {
      defaultChoice orElse SimpleReader.readLine("Push changes to the remote repository (y/n)? [y] ") match {
        case Yes() | Some("") =>
          if (vc == Git) st.log.info("git push sends its console output to standard error, which will cause the next few lines to be marked as [error].")
          vcs(st).pushChanges !! st.log
        case _ => st.log.warn("Remember to push the changes yourself!")
      }
    } else {
      st.log.info("Changes were NOT pushed, because no upstream branch is configured for the local branch [%s]" format vcs(st).currentBranch)
    }
    st
  }

  lazy val publishArtifacts = ReleaseStep(
    action = publishArtifactsAction,
    check = st => {
      // getPublishTo fails if no publish repository is set up.
      val ex = st.extract
      val ref = ex.get(thisProjectRef)
      Classpaths.getPublishTo(ex.get(publishTo in Global in ref))
      st
    },
    enableCrossBuild = true
  )
  private[sbtrelease] lazy val publishArtifactsAction = { st: State =>
    val extracted = st.extract
    val ref = extracted.get(thisProjectRef)
    extracted.runAggregated(publish in Global in ref, st)
  }

  private def readVersion(ver: String, prompt: String, useDef: Boolean): String = {
    if (useDef) ver
    else SimpleReader.readLine(prompt format ver) match {
      case Some("") => ver
      case Some(input) => Version(input).map(_.string).getOrElse(versionFormatError)
      case None => sys.error("No version provided!")
    }
  }

  def reapply(settings: Seq[Setting[_]], state: State): State = {
    val extracted = state.extract
    import extracted._

    val append = Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, settings)

    // We don't want even want to be able to save the settings that are applied to the session during the release cycle.
    // Just using an empty string works fine and in case the user calls `session save`, empty lines will be generated.
		val newSession = session.appendSettings( append map (a => (a, SbtCompat.EmptySetting)))
		BuiltinCommands.reapply(newSession, structure, state)
  }


  // This is a copy of the state function for the command Cross.switchVersion
  private[sbtrelease] def switchScalaVersion(state: State, version: String): State = {
    val x = Project.extract(state)
    import x._
    state.log.info("Setting scala version to " + version)
    val add = (scalaVersion in GlobalScope := version) :: (scalaHome in GlobalScope := None) :: Nil
    val cleared = session.mergeSettings.filterNot(Cross.crossExclude)
    val newStructure = Load.reapply(add ++ cleared, structure)
    Project.setProject(session, newStructure, state)
  }

  private[sbtrelease] def runCrossBuild(func: State => State): State => State = { state =>
    val x = Project.extract(state)
    import x._
    val versions = Cross.crossVersions(state)
    val current = scalaVersion in currentRef get structure.data
    val finalS = (state /: versions) {
      case (s, v) => func(switchScalaVersion(s, v))
    }
    current.map(switchScalaVersion(finalS, _)).getOrElse(finalS)
  }
}

object ExtraReleaseCommands {
  import ReleaseStateTransformations._

  private lazy val initialVcsChecksCommandKey = "release-vcs-checks"
  lazy val initialVcsChecksCommand = Command.command(initialVcsChecksCommandKey)(initialVcsChecks)

  private lazy val checkSnapshotDependenciesCommandKey = "release-check-snapshot-dependencies"
  lazy val checkSnapshotDependenciesCommand = Command.command(checkSnapshotDependenciesCommandKey)(checkSnapshotDependencies)

  private lazy val inquireVersionsCommandKey = "release-inquire-versions"
  lazy val inquireVersionsCommand = Command.command(inquireVersionsCommandKey)(inquireVersions)

  private lazy val setReleaseVersionCommandKey = "release-set-release-version"
  lazy val setReleaseVersionCommand = Command.command(setReleaseVersionCommandKey)(setReleaseVersion)

  private lazy val setNextVersionCommandKey = "release-set-next-version"
  lazy val setNextVersionCommand = Command.command(setNextVersionCommandKey)(setNextVersion)

  private lazy val commitReleaseVersionCommandKey = "release-commit-release-version"
  lazy val commitReleaseVersionCommand =  Command.command(commitReleaseVersionCommandKey)(commitReleaseVersion)

  private lazy val commitNextVersionCommandKey = "release-commit-next-version"
  lazy val commitNextVersionCommand = Command.command(commitNextVersionCommandKey)(commitNextVersion)

  private lazy val tagReleaseCommandKey = "release-tag-release"
  lazy val tagReleaseCommand = Command.command(tagReleaseCommandKey)(tagRelease)

  private lazy val pushChangesCommandKey = "release-push-changes"
  lazy val pushChangesCommand = Command.command(pushChangesCommandKey)(pushChanges)
}


object Utilities {

  class StateW(st: State) {
    def extract = Project.extract(st)
  }
  implicit def stateW(st: State): StateW = new StateW(st)

  private[sbtrelease] def resolve[T](key: ScopedKey[T], extracted: Extracted): ScopedKey[T] =
		Project.mapScope(Scope.resolveScope(GlobalScope, extracted.currentRef.build, extracted.rootProject) )( key.scopedKey )

  object Yes {
    def unapply(s: Option[String]) = s.exists(_.toLowerCase == "y")
  }

  def extractDefault(st: State, default: String): Option[String] = {
    val useDefs = st.get(useDefaults).getOrElse(false)
    if (useDefs) Some(default)
    else None
  }

}

