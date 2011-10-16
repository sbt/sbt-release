package sbtrelease

import sbt._

object Git {

  private def cmd(args: Any*): ProcessBuilder = Process("git" +: args.map(_.toString))

  def trackingBranch: String = (cmd("for-each-ref", "--format=%(upstream:short)", "refs/heads/" + currentBranch) !!) replaceAll("\n", "")

  def currentBranch = (cmd("name-rev", "HEAD", "--name-only") !!).replaceAll("\n", "")

  def add(files: String*) = cmd(("add" +: files): _*)

  def commit(message: String) = cmd("commit", "-m", message)

  def tag(name: String) = cmd("tag", name)

  def pushTags = cmd("push", "--tags")

  def status = cmd("status", "--porcelain")

  def pushCurrentBranch = {
    val localBranch = currentBranch
    val Array(remoteRepo, remoteBranch)  = trackingBranch.split("/")
    cmd("push", remoteRepo, "%s:%s" format (localBranch, remoteBranch))
  }

  def resetHard(hash: String) = cmd("reset", "--hard", hash)
}
