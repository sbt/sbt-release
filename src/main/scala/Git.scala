package sbtrelease

import sbt._

object Git {
  private val devnull = new ProcessLogger {
    def info(s: => String) {}
    def error(s: => String) {}
    def buffer[T](f: => T): T = f
  }

  private lazy val gitExec = {
    val maybeOsName = sys.props.get("os.name").map(_.toLowerCase)
    val maybeIsWindows = maybeOsName.filter(_.contains("windows"))
    maybeIsWindows.map(_ => "git.exe").getOrElse("git")
  }

  private def cmd(args: Any*): ProcessBuilder = Process(gitExec +: args.map(_.toString))

  private val trackingBranchCmd = cmd("config", "branch.%s.merge" format currentBranch)
  def trackingBranch: String = (trackingBranchCmd !!).trim.stripPrefix("refs/heads/")

  private val trackingRemoteCmd: ProcessBuilder = cmd("config", "branch.%s.remote" format currentBranch)
  def trackingRemote: String = (trackingRemoteCmd !!) trim

  def hasUpstream = trackingRemoteCmd ! devnull == 0 && trackingBranchCmd ! devnull == 0

  def currentBranch =  (cmd("symbolic-ref", "HEAD") !!).trim.stripPrefix("refs/heads/")

  def currentHash = (cmd("rev-parse", "HEAD") !!) trim

  def add(files: String*) = cmd(("add" +: files): _*)

  def commit(message: String) = cmd("commit", "-m", message)

  def tag(name: String, force: Boolean = false) = cmd("tag", "-a", name, "-m", "Releasing " + name, if(force) "-f" else "")

  def existsTag(name: String) = cmd("show-ref", "--quiet", "--tags", "--verify", "refs/tags/" + name) ! devnull == 0

  def pushTags = cmd("push", "--tags", trackingRemote)

  def status = cmd("status", "--porcelain")

  def pushCurrentBranch = {
    val localBranch = currentBranch
    cmd("push", trackingRemote, "%s:%s" format (localBranch, trackingBranch))
  }

  def resetHard(hash: String) = cmd("reset", "--hard", hash)
}
