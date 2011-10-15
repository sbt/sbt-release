package sbtrelease

import util.control.Exception._

private[sbtrelease] object Version {
  val VersionR = """([0-9]+)(?:(?:\.([0-9]+))?(?:\.([0-9]+))?)?(-.*)?""".r

  def apply(s: String): Option[Version] = {
    allCatch opt {
      val VersionR(maj, min, mic, qual) = s
      Version(maj.toInt, Option(min).map(_.toInt), Option(mic).map(_.toInt), Option(qual))
    }
  }
}

private[sbtrelease] case class Version(major: Int, minor: Option[Int], micro: Option[Int], qualifier: Option[String]) {
  def bump = {
    val maybeBumpedMicro = micro.map(m => copy(micro = Some(m + 1)))
    val maybeBumpedMinor = minor.map(m => copy(minor = Some(m + 1)))
    lazy val bumpedMajor = copy(major = major + 1)

    maybeBumpedMicro.orElse(maybeBumpedMinor).getOrElse(bumpedMajor)
  }

  def withoutQualifier = copy(qualifier = None)
  def asSnapshot = copy(qualifier = Some("-SNAPSHOT"))

  def string = "" + major + get(minor) + get(micro) + qualifier.getOrElse("")

  private def get(part: Option[Int]) = part.map("." + _).getOrElse("")
}
