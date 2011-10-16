package sbtrelease

import sbt._

object Git {

  private def cmd(args: Any*): ProcessBuilder = Process("git" +: args.map(_.toString))

  def trackingBranch: String = (cmd("for-each-ref", "--format=%(upstream:short)", "refs/heads/" + currentBranch) !!) replaceAll("\n", "")

  def currentBranch = (cmd("name-rev", "HEAD", "--name-only") !!).replaceAll("\n", "")

  def commit(message: String, files: String*) = cmd((Seq("commit", "-m", message) ++ files): _*)

  def tag(name: String) = cmd("tag", name)

  def pushTags = cmd("push", "--tags")

  def pushCurrentBranch = {
    val localBranch = currentBranch
    val Array(remoteRepo, remoteBranch)  = trackingBranch.split("/")
    cmd("push", remoteRepo, "%s:%s" format (localBranch, remoteBranch))
  }

  def resetHard(hash: String) = cmd("reset", "--hard", hash)
}
