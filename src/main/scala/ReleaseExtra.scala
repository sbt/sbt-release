package sbtrelease

import java.io.File
import sbt._
import Keys._

object ReleaseStateTransformations {
  import Release.ReleaseKeys._
  import Utilities._

  lazy val initialGitChecks: ReleasePart = { st =>
    if (!new File(".git").exists) {
      sys.error("Aborting release. Working directory is not a git repository.")
    }
    val status = (Git.status !!).trim
    if (!status.isEmpty) {
      sys.error("Aborting release. Working directory is dirty.")
    }
    st.logger.info("Starting release process off git commit: " + Git.currentHash)
    st
  }


  lazy val checkSnapshotDependencies: ReleasePart = { st =>
    val snapshotDeps = st.extract.evalTask(snapshotDependencies, st)
    val useDefs = st.get(useDefaults).getOrElse(false)
    if (!snapshotDeps.isEmpty) {
      if (useDefs) {
        sys.error("Aborting release due to snapshot dependencies.")
      } else {
        st.logger.warn("Snapshot dependencies detected:\n" + snapshotDeps.mkString("\n"))
        SimpleReader.readLine("Do you want to continue (y/n)? [n] ") match {
          case Some("y") | Some("Y") =>
          case _ => sys.error("Aborting release due to snapshot dependencies.")
        }
      }
    }
    st
  }


  lazy val inquireVersions: ReleasePart = { st =>
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


  lazy val runTest: ReleasePart = {st =>
    if (!st.get(skipTests).getOrElse(false)) {
      val extracted = Project.extract(st)
      val ref = extracted.get(thisProjectRef)
      extracted.evalTask(test in Test in ref, st)
    }
    st
  }

  lazy val setReleaseVersion: ReleasePart = setVersion(_._1)
  lazy val setNextVersion: ReleasePart = setVersion(_._2)
  private def setVersion(selectVersion: Versions => String): ReleasePart =  { st =>
    val vs = st.get(versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
    val selected = selectVersion(vs)

    st.logger.info("Setting version to '%s'." format selected)


    val versionString = "%sversion in ThisBuild := \"%s\"%s" format (lineSep, selected, lineSep)
    IO.write(new File("version.sbt"), versionString)

    reapply(Seq(
      version in ThisBuild := selected
    ), st)
  }

  lazy val commitReleaseVersion: ReleasePart = commitVersion("Releasing %s")
  lazy val commitNextVersion: ReleasePart = commitVersion("Bump to %s")
  private def commitVersion(msgPattern: String): ReleasePart = { st =>
    val v = st.extract.get(version in ThisBuild)

    Git.add("version.sbt") !! st.logger
    Git.commit(msgPattern format v) !! st.logger

    st
  }

  lazy val tagRelease: ReleasePart = { st =>
    val tag = "v" + st.extract.get(version in ThisBuild)

    Git.tag(tag) !! st.logger

    st
  }

  private def readVersion(ver: String, prompt: String, useDef: Boolean): String = {
    if (useDef) ver
    else SimpleReader.readLine(prompt format ver) match {
      case Some("") => ver
      case Some(input) => Version(input).map(_.string).getOrElse(versionFormatError)
      case None => sys.error("No version provided!")
    }
  }

  def reapply(settings: Seq[Setting[_]], state: State) = {
    val extracted = state.extract
    import extracted._

    val append = Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, settings)

    // We don't want even want to be able to save the settings that are applied to the session during the release cycle.
    // Just using an empty string works fine and in case the user calls `session save`, empty lines will be generated.
    val EmptySettingString = ""
		val newSession = session.appendSettings( append map (a => (a, EmptySettingString)))
		BuiltinCommands.reapply(newSession, structure, state)
  }
}


object ExtraReleaseCommands {
  import ReleaseStateTransformations._

  private lazy val initialGitChecksCommandKey = "release-git-checks"
  lazy val initialGitChecksCommand = Command.command(initialGitChecksCommandKey)(initialGitChecks)

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

}


object Utilities {
  val lineSep = sys.props.get("line.separator").getOrElse(sys.error("No line separator? Really?"))

  class StateW(st: State) {
    def logger = CommandSupport.logger(st)
    def extract = Project.extract(st)
  }
  implicit def stateW(st: State): StateW = new StateW(st)
}

