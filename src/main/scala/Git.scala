package sbtrelease

import sbt._

object Git {
  import Utilities._

  private lazy val gitExec = {
    val maybeOsName = sys.props.get("os.name").map(_.toLowerCase)
    val maybeIsWindows = maybeOsName.filter(_.contains("windows"))
    maybeIsWindows.map(_ => "git.exe").getOrElse("git")
  }

  private def cmd(args: Any*): ProcessBuilder = Process(gitExec +: args.map(_.toString))

  def trackingBranch: String = (cmd("for-each-ref", "--format=%(upstream:short)", "refs/heads/" + currentBranch) !!) trim

  def currentBranch = (cmd("name-rev", "HEAD", "--name-only") !!) trim

  def currentHash = (cmd("rev-parse", "HEAD") !!) trim

  def add(files: String*) = cmd(("add" +: files): _*)

  def commit(message: String) = cmd("commit", "-m", message)

  def tag(name: String) = cmd("tag", "-a", name, "-m", "Releasing " + name)

  def pushTags = cmd("push", "--tags")

  def status = cmd("status", "--porcelain")

  def pushCurrentBranch = {
    val localBranch = currentBranch
    val Array(remoteRepo, remoteBranch)  = trackingBranch.split("/")
    cmd("push", remoteRepo, "%s:%s" format (localBranch, remoteBranch))
  }

  def resetHard(hash: String) = cmd("reset", "--hard", hash)
}
