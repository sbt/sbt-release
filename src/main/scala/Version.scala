package sbtrelease

import util.control.Exception._

object Version {
  sealed trait Bump {
    def bump: Version => Version
  }

  object Bump {
    object Major extends Bump { def bump = _.bumpMajor }
    object Minor extends Bump { def bump = _.bumpMinor }
    object Bugfix extends Bump { def bump = _.bumpBugfix }
    object Next extends Bump { def bump = _.bump }

    val default = Minor
  }

  val VersionR = """([0-9]+)(?:(?:\.([0-9]+))?(?:\.([0-9]+))?)?([\-0-9a-zA-Z]*)?""".r

  def apply(s: String): Option[Version] = {
    allCatch opt {
      val VersionR(maj, min, mic, qual) = s
      Version(maj.toInt, Option(min).map(_.toInt), Option(mic).map(_.toInt), Option(qual).filterNot(_.isEmpty))
    }
  }
}

case class Version(major: Int, minor: Option[Int], bugfix: Option[Int], qualifier: Option[String]) {
  def bump = {
    val maybeBumpedBugfix = bugfix.map(m => copy(bugfix = Some(m + 1)))
    val maybeBumpedMinor = minor.map(m => copy(minor = Some(m + 1)))
    lazy val bumpedMajor = copy(major = major + 1)

    maybeBumpedBugfix.orElse(maybeBumpedMinor).getOrElse(bumpedMajor)
  }

  def bumpMajor = copy(major = major + 1, minor = minor.map(_ => 0), bugfix = bugfix.map(_ => 0))
  def bumpMinor = copy(minor = minor.map(_ + 1), bugfix = bugfix.map(_ => 0))
  def bumpBugfix = copy(bugfix = bugfix.map(_ + 1))

  def bump(bumpType: Version.Bump): Version = bumpType.bump(this)

  def withoutQualifier = copy(qualifier = None)
  def asSnapshot = copy(qualifier = Some("-SNAPSHOT"))

  def string = "" + major + get(minor) + get(bugfix) + qualifier.getOrElse("")

  private def get(part: Option[Int]) = part.map("." + _).getOrElse("")
}
