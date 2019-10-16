package sbtrelease

import sbt._
import java.io.File

import sys.process.{ Process, ProcessBuilder, ProcessLogger }
import Compat._

trait Vcs {
  val commandName: String

  val baseDir: File

  def cmd(args: Any*): ProcessBuilder
  def status: ProcessBuilder
  def currentHash: String
  def add(files: String*): ProcessBuilder
  def commit(message: String, sign: Boolean, signOff: Boolean): ProcessBuilder
  def existsTag(name: String): Boolean
  def checkRemote(remote: String): ProcessBuilder
  def tag(name: String, comment: String, sign: Boolean): ProcessBuilder
  def hasUpstream: Boolean
  def trackingRemote: String
  def isBehindRemote: Boolean
  def pushChanges: ProcessBuilder
  def currentBranch: String
  def hasUntrackedFiles: Boolean = untrackedFiles.nonEmpty
  def untrackedFiles: Seq[String]
  def hasModifiedFiles: Boolean = modifiedFiles.nonEmpty
  def modifiedFiles: Seq[String]

  protected def executableName(command: String) = {
    val maybeOsName = sys.props.get("os.name").map(_.toLowerCase)
    val maybeIsWindows = maybeOsName.filter(_.contains("windows"))
    maybeIsWindows.map(_ => command+".exe").getOrElse(command)
  }

  protected val devnull: ProcessLogger = new ProcessLogger {
    override def out(s: => String): Unit = {}
    override def err(s: => String): Unit = {}
    override def buffer[T](f: => T): T = f
  }

  def stdErrorToStdOut(delegate: ProcessLogger): ProcessLogger = new ProcessLogger {
    override def out(s: => String): Unit = delegate.out(s)
    override def err(s: => String): Unit = delegate.out(s)
    override def buffer[T](f: => T): T = delegate.buffer(f)
  }
}

object Vcs {
  def detect(dir: File): Option[Vcs] = {
    Stream(Git, Mercurial, Subversion).flatMap(comp => comp.isRepository(dir).map(comp.mkVcs(_))).headOption
  }
}

trait GitLike extends Vcs {
  private lazy val exec = executableName(commandName)

  def cmd(args: Any*): ProcessBuilder = Process(exec +: args.map(_.toString), baseDir)

  def add(files: String*) = cmd(("add" +: files): _*)
}

trait VcsCompanion {
  protected val markerDirectory: String

  // Using the new git worktree feature the dir is now a file, so checking for exists should be enough
  def isRepository(dir: File): Option[File] =
    if (new File(dir, markerDirectory).exists) Some(dir)
    else Option(dir.getParentFile).flatMap(isRepository)

  def mkVcs(baseDir: File): Vcs
}


object Mercurial extends VcsCompanion {
  protected val markerDirectory = ".hg"

  def mkVcs(baseDir: File) = new Mercurial(baseDir)
}

class Mercurial(val baseDir: File) extends Vcs with GitLike {
  val commandName = "hg"

  private def andSign(sign: Boolean, proc: ProcessBuilder) =
    if (sign)
      proc #&& cmd("sign")
    else
      proc

  def status = cmd("status")

  def currentHash = cmd("identify", "-i").!!.trim

  def existsTag(name: String) = cmd("tags").!!.linesIterator.exists(_.endsWith(" "+name))

  def commit(message: String, sign: Boolean, signOff: Boolean) =
    andSign(sign, cmd("commit", "-m", message))

  def tag(name: String, comment: String, sign: Boolean) =
    andSign(sign, cmd("tag", "-f", "-m", comment, name))

  def hasUpstream = cmd("paths", "default") ! devnull == 0

  def trackingRemote = "default"

  def isBehindRemote = cmd("incoming", "-b", ".", "-q") ! devnull == 0

  def pushChanges = cmd("push", "-b", ".")

  def currentBranch = cmd("branch").!!.trim

  // FIXME: This is utterly bogus, but I cannot find a good way...
  def checkRemote(remote: String) = cmd("id", "-n")

  def untrackedFiles = cmd("status", "-un").lineStream
  def modifiedFiles = cmd("status", "-mn").lineStream
}

object Git extends VcsCompanion {
  protected val markerDirectory = ".git"

  def mkVcs(baseDir: File) = new Git(baseDir)
}

class Git(val baseDir: File) extends Vcs with GitLike {
  val commandName = "git"


  private lazy val trackingBranchCmd = cmd("config", "branch.%s.merge" format currentBranch)
  private def trackingBranch: String = trackingBranchCmd.!!.trim.stripPrefix("refs/heads/")

  private lazy val trackingRemoteCmd: ProcessBuilder = cmd("config", "branch.%s.remote" format currentBranch)
  def trackingRemote: String = trackingRemoteCmd.!!.trim

  def hasUpstream = trackingRemoteCmd ! devnull == 0 && trackingBranchCmd ! devnull == 0

  def currentBranch =  cmd("symbolic-ref", "HEAD").!!.trim.stripPrefix("refs/heads/")

  def currentHash = revParse("HEAD")

  private def revParse(name: String) = cmd("rev-parse", name).!!.trim

  def isBehindRemote = (cmd("rev-list", "%s..%s/%s".format(currentBranch, trackingRemote, trackingBranch)) !! devnull).trim.nonEmpty

  private final case class GitFlag(on: Boolean, flag: String)

  private def withFlags(flags: Seq[GitFlag])(args: String*): Seq[String] = {
    val appended = flags.collect {
      case GitFlag(true, flag) => s"-$flag"
    }

    args ++ appended
  }

  def commit(message: String, sign: Boolean, signOff: Boolean) = {
    val gitFlags = List(GitFlag(sign, "S"), GitFlag(signOff, "s"))
    cmd(withFlags(gitFlags)("commit", "-m", message): _*)
  }

  def tag(name: String, comment: String, sign: Boolean) =
    cmd(withFlags(List(GitFlag(sign, "s")))("tag", "-f", "-a", name, "-m", comment): _*)

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

  def untrackedFiles = cmd("ls-files", "--other", "--exclude-standard").lineStream
  def modifiedFiles = cmd("ls-files", "--modified", "--exclude-standard").lineStream
}

object Subversion extends VcsCompanion {
  override def mkVcs(baseDir: File): Vcs = new Subversion(baseDir)

  override protected val markerDirectory: String = ".svn"
}

class Subversion(val baseDir: File) extends Vcs {
  override val commandName = "svn"
  private lazy val exec = executableName(commandName)

  override def cmd(args: Any*): ProcessBuilder = Process(exec +: args.map(_.toString), baseDir)

  override def modifiedFiles = cmd("status", "-q").lineStream
  override def untrackedFiles = cmd("status").lineStream.filter(_.startsWith("?"))

  override def add(files: String*) = {
    val filesToAdd = files.filterNot(isFileUnderVersionControl)
    if(!filesToAdd.isEmpty) cmd(("add" +: filesToAdd): _*) else noop
  }

  override def commit(message: String, sign: Boolean, signOff: Boolean) = {
    require(!sign, "Signing not supported in Subversion.")
    require(!signOff, "Signing off not supported in Subversion.")
    cmd("commit", "-m", message)
  }

  override def currentBranch: String = workingDirSvnUrl.substring(workingDirSvnUrl.lastIndexOf("/") + 1)

  override def pushChanges: ProcessBuilder = commit("push changes", false, false)

  override def isBehindRemote: Boolean = false

  override def trackingRemote: String = ""

  override def hasUpstream: Boolean = true

  override def tag(name: String, comment: String, sign: Boolean): ProcessBuilder = {
    require(!sign, "Signing not supported in Subversion.")
    val tagUrl = getSvnTagUrl(name)
    if(existsTag(name)) {
      val deleteTagComment = comment + ", \ndelete tag " + name + " to create a new one."
      cmd("del", tagUrl, "-m", deleteTagComment).!!
    }
    cmd("copy", workingDirSvnUrl, tagUrl, "-m", comment)
  }

  override def checkRemote(remote: String): ProcessBuilder = noop

  override def existsTag(name: String): Boolean = {
    Try(cmd("info", getSvnTagUrl(name)).!!).nonEmpty
  }

  override def currentHash: String = ""

  override def status: ProcessBuilder = cmd("status", "-q")

  lazy val workingDirSvnUrl:String = {
    val svnInfo = cmd("info").!!
    val svnInfoUrlKey = "URL: "
    val urlStartIdx = svnInfo.indexOf(svnInfoUrlKey) + svnInfoUrlKey.length
    svnInfo.substring(urlStartIdx, svnInfo.indexOf('\n', urlStartIdx)-1)
  }

  lazy val repoRoot:String = {
    val svnBaseUrlEndIdxOptions = List(
      workingDirSvnUrl.indexOf("/trunk"),
      workingDirSvnUrl.indexOf("/branches"),
      workingDirSvnUrl.indexOf("/tags")
    ).filter(_ >= 0)
    require(!svnBaseUrlEndIdxOptions.isEmpty, "No /trunk, /branches or /tags part found in svn url. Base url cannot be extracted.")
    val svnBaseUrlEndIdx = svnBaseUrlEndIdxOptions.head
    workingDirSvnUrl.substring(0, svnBaseUrlEndIdx + 1)
  }

  private def getSvnTagUrl(name: String): String = repoRoot + "tags/" + name

  private def isFileUnderVersionControl(file: String): Boolean = Try(cmd("info", file).!!).nonEmpty

  private def noop:ProcessBuilder = status
}

private[sbtrelease] object Try {
  def apply[A](f: => A): Option[A] = scala.util.control.Exception.allCatch.opt(f)
}
