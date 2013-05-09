package sbtrelease

import sbt._
import java.io.File

trait Vcs {
  val commandName: String

  def cmd(args: Any*): ProcessBuilder
  def status: ProcessBuilder
  def currentHash: String
  def add(files: String*): ProcessBuilder
  def addAll: ProcessBuilder
  def commit(message: String): ProcessBuilder
  def existsTag(name: String): Boolean
  def checkRemote(remote: String): ProcessBuilder
  def tag(name: String, comment: String, force: Boolean = false): ProcessBuilder
  def hasUpstream: Boolean
  def trackingRemote: String
  def isBehindRemote: Boolean
  def pushChanges: ProcessBuilder
  def currentBranch: String
  def isRepository(dir: File): Boolean

  protected def executableName(command: String) = {
    val maybeOsName = sys.props.get("os.name").map(_.toLowerCase)
    val maybeIsWindows = maybeOsName.filter(_.contains("windows"))
    maybeIsWindows.map(_ => command+".exe").getOrElse(command)
  }

  protected val devnull = new ProcessLogger {
    def info(s: => String) {}
    def error(s: => String) {}
    def buffer[T](f: => T): T = f
  }
}

object Vcs {
  def detect(dir: File): Option[Vcs] = {
    Seq(Git, Mercurial).find(_.isRepository(dir))
  }
}

trait GitLike extends Vcs {
  private lazy val exec = executableName(commandName)

  protected val markerDirectory: String

  def cmd(args: Any*): ProcessBuilder = Process(exec +: args.map(_.toString))

  def isRepository(dir: File): Boolean =
    new File(dir, markerDirectory).isDirectory || Option(dir.getParentFile).exists(isRepository)

  def add(files: String*) = cmd(("add" +: files): _*)

  def commit(message: String) = cmd("commit", "-m", message)
}

object Mercurial extends Vcs with GitLike {
  val commandName = "hg"

  protected val markerDirectory = ".hg"

  def addAll = cmd("add")

  def status = cmd("status")

  def currentHash = (cmd("identify", "-i") !!) trim

  def existsTag(name: String) = (cmd("tags") !!).linesIterator.exists(_.endsWith(" "+name))

  def tag(name: String, comment: String, force: Boolean) = cmd("tag", if(force) "-f" else "", "-m", comment, name)

  def hasUpstream = cmd("paths", "default") ! devnull == 0

  def trackingRemote = "default"

  def isBehindRemote = cmd("incoming", "-b", ".", "-q") ! devnull == 0

  def pushChanges = cmd("push", "-b", ".")

  def currentBranch = (cmd("branch") !!) trim

  // FIXME: This is utterly bogus, but I cannot find a good way...
  def checkRemote(remote: String) = cmd("id", "-n")
}

object Git extends Vcs with GitLike {
  val commandName = "git"

  protected val markerDirectory = ".git"

  private lazy val trackingBranchCmd = cmd("config", "branch.%s.merge" format currentBranch)
  private def trackingBranch: String = (trackingBranchCmd !!).trim.stripPrefix("refs/heads/")

  private lazy val trackingRemoteCmd: ProcessBuilder = cmd("config", "branch.%s.remote" format currentBranch)
  def trackingRemote: String = (trackingRemoteCmd !!) trim

  def addAll = cmd("add","-A",".")

  def hasUpstream = trackingRemoteCmd ! devnull == 0 && trackingBranchCmd ! devnull == 0

  def currentBranch =  (cmd("symbolic-ref", "HEAD") !!).trim.stripPrefix("refs/heads/")

  def currentHash = revParse(currentBranch)

  private def revParse(name: String) = (cmd("rev-parse", name) !!) trim

  def isBehindRemote = (cmd("rev-list", "%s..%s/%s".format(currentBranch, trackingRemote, trackingBranch)) !! devnull).trim.nonEmpty

  def tag(name: String, comment: String, force: Boolean = false) = cmd("tag", "-a", name, "-m", comment, if(force) "-f" else "")

  def existsTag(name: String) = cmd("show-ref", "--quiet", "--tags", "--verify", "refs/tags/" + name) ! devnull == 0

  def checkRemote(remote: String) = fetch(remote)

  def fetch(remote: String) = cmd("fetch", remote)

  def status = cmd("status", "--porcelain")

  def pushChanges = pushCurrentBranch #&& pushTags

  private def pushCurrentBranch = {
    val localBranch = currentBranch
    cmd("push", trackingRemote, "%s:%s" format (localBranch, trackingBranch))
  }

  private def pushTags = cmd("push", "--tags", trackingRemote)
}
