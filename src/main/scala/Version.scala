package sbtrelease

import util.control.Exception._

object Version {
  sealed trait Bump {
    def bump: Version => Version
  }

  object Bump {

    /**
     * Strategy to always bump the major version by default. Ex. 1.0.0 would be bumped to 2.0.0
     */
    case object Major extends Bump { def bump: Version => Version = _.bumpMajor }
    /**
     * Strategy to always bump the minor version by default. Ex. 1.0.0 would be bumped to 1.1.0
     */
    case object Minor extends Bump { def bump: Version => Version = _.bumpMinor }
    /**
     * Strategy to always bump the bugfix version by default. Ex. 1.0.0 would be bumped to 1.0.1
     */
    case object Bugfix extends Bump { def bump: Version => Version = _.bumpBugfix }
    /**
     * Strategy to always bump the nano version by default. Ex. 1.0.0.0 would be bumped to 1.0.0.1
     */
    case object Nano extends Bump { def bump: Version => Version = _.bumpNano }

    case object Next extends Bump { def bump: Version => Version = _.bump }
    /**
     * Strategy to always increment to the next version from smallest to greatest
     * Ex:
     * Major: 1 becomes 2
     * Minor: 1.0 becomes 1.1
     * Bugfix: 1.0.0 becomes 1.0.1
     * Nano: 1.0.0.0 becomes 1.0.0.1
     * Qualifier with version number: 1.0-RC1 becomes 1.0-RC2
     * Qualifier without version number: 1.0-alpha becomes 1.0
     */
    case object NextWithQualifier extends Bump { def bump: Version => Version = _.bumpWithQualifier }

    val default = Next
  }

  val VersionR = """([0-9]+)((?:\.[0-9]+)+)?([\.\-0-9a-zA-Z]*)?""".r
  val PreReleaseQualifierR = """[\.-](?i:rc|m|alpha|beta)[\.-]?[0-9]*""".r

  def apply(s: String): Option[Version] = {
    allCatch opt {
      val VersionR(maj, subs, qual) = s
      // parse the subversions (if any) to a Seq[Int]
      val subSeq: Seq[Int] = Option(subs) map { str =>
        // split on . and remove empty strings
        str.split('.').filterNot(_.trim.isEmpty).map(_.toInt).toSeq
      } getOrElse Nil
      Version(maj.toInt, subSeq, Option(qual).filterNot(_.isEmpty))
    }
  }
}

case class Version(major: Int, subversions: Seq[Int], qualifier: Option[String]) {
  def bump = {
    val maybeBumpedPrerelease = qualifier.collect {
      case Version.PreReleaseQualifierR() => withoutQualifier
    }
    def maybeBumpedLastSubversion = bumpSubversionOpt(subversions.length-1)
    def bumpedMajor = copy(major = major + 1)

    maybeBumpedPrerelease
      .orElse(maybeBumpedLastSubversion)
      .getOrElse(bumpedMajor)
  }

  def bumpWithQualifier = {
    val maybeBumpedPrerelease = qualifier.collect {
      case rawQualifier @ Version.PreReleaseQualifierR() =>
        val qualifierEndsWithNumberRegex = """[0-9]*$""".r

        val opt = for {
          versionNumberQualifierStr <- qualifierEndsWithNumberRegex.findFirstIn(rawQualifier)
          versionNumber <- Try(versionNumberQualifierStr.toInt)
            .toRight(new Exception(s"Version number not parseable to a number. Version number received: $versionNumberQualifierStr"))
            .toOption
          newVersionNumber = versionNumber + 1
          newQualifier = rawQualifier.replaceFirst(versionNumberQualifierStr, newVersionNumber.toString)
        } yield Version(major, subversions, Some(newQualifier))

        opt.getOrElse(copy(qualifier = None))
    }
    def maybeBumpedLastSubversion = bumpSubversionOpt(subversions.length-1)
    def bumpedMajor = copy(major = major + 1)

    maybeBumpedPrerelease
      .orElse(maybeBumpedLastSubversion)
      .getOrElse(bumpedMajor)
  }

  def bumpMajor  = copy(major = major + 1, subversions = Seq.fill(subversions.length)(0))
  def bumpMinor  = maybeBumpSubversion(0)
  def bumpBugfix = maybeBumpSubversion(1)
  def bumpNano   = maybeBumpSubversion(2)

  def maybeBumpSubversion(idx: Int) = bumpSubversionOpt(idx) getOrElse this

  private def bumpSubversionOpt(idx: Int) = {
    val bumped = subversions.drop(idx)
    val reset = bumped.drop(1).length
    bumped.headOption map { head =>
      val patch = (head+1) +: Seq.fill(reset)(0)
      copy(subversions = subversions.patch(idx, patch, patch.length))
    }
  }

  def bump(bumpType: Version.Bump): Version = bumpType.bump(this)

  def withoutQualifier = copy(qualifier = None)
  def asSnapshot = copy(qualifier = qualifier.map { qualifierStr =>
    s"$qualifierStr-SNAPSHOT"
  }.orElse(Some("-SNAPSHOT")))

  def withoutSnapshot: Version = copy(qualifier = qualifier.flatMap { qualifierStr =>
    val snapshotRegex = """-SNAPSHOT""".r
    val newQualifier = snapshotRegex.replaceFirstIn(qualifierStr, "")
    if (newQualifier == qualifierStr) {
      None
    } else {
      Some(newQualifier)
    }
  })

  def string = "" + major + mkString(subversions) + qualifier.getOrElse("")

  private def mkString(parts: Seq[Int]) = parts.map("."+_).mkString
}
