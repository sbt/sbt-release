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
    val extracted = Project.extract(st)
    val snapshotDeps = extracted.evalTask(snapshotDependencies, st)
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
    val (releaseVersion, nextVersion) = extracted.get(versions)

    val useDefs = st.get(useDefaults).getOrElse(false)

    val releaseV =
    if (useDefs) releaseVersion
      else readVersion(releaseVersion, "Release version [%s] : " format releaseVersion)

    val nextV =
      if (useDefs) nextVersion
      else readVersion(nextVersion, "Next version [%s] : " format nextVersion)

    extracted.append(Seq(versions := (releaseV, nextV)), st)
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
    val extracted = Project.extract(st)
    val vs = extracted.get(versions)
    val selected = selectVersion(vs)

    st.logger.info("Setting version to '%s'." format selected)


    val versionString = "%sversion in ThisBuild := \"%s\"%s" format (lineSep, selected, lineSep)
    IO.write(new File("version.sbt"), versionString)

    extracted.append(Seq(
      version in ThisBuild := selected,
      versions := vs
    ), st)
  }

  lazy val commitReleaseVersion: ReleasePart = commitVersion("Releasing %s")
  lazy val commitNextVersion: ReleasePart = commitVersion("Bump to %s")
  private def commitVersion(msgPattern: String): ReleasePart = { st =>
    val extracted = Project.extract(st)
    val v = extracted.get(version in ThisBuild)

    Git.add("version.sbt") !! st.logger
    Git.commit(msgPattern format v) !! st.logger

    st
  }

  lazy val tagRelease: ReleasePart = { st =>
    val extracted = Project.extract(st)
    val v = extracted.get(version in ThisBuild)

    Git.tag("v" + v) !! st.logger

    st
  }

  private def readVersion(ver: String, prompt: String): String = {
    SimpleReader.readLine(prompt) match {
      case Some("") => ver
      case Some(input) => Version(input).map(_.string).getOrElse(versionFormatError)
      case None => sys.error("No version provided!")
    }
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
  }
  implicit def stateW(st: State): StateW = new StateW(st)
}

