package sbtrelease

import org.specs2.mutable.Specification

object VersionSpec extends Specification {

  def version(v: String) = Version(v) match {
    case Some(parsed) => parsed
    case None => sys.error("Can't parse version " + v)
  }

  "Version bumping" should {
    def bump(v: String) = version(v).bump.string

    "bump the major version if there's only a major version" in {
      bump("1") must_== "2"
    }
    "bump the minor version if there's only a minor version" in {
      bump("1.2") must_== "1.3"
    }
    "bump the bugfix version if there's only a bugfix version" in {
      bump("1.2.3") must_== "1.2.4"
    }
    "drop the qualifier if it's a pre release" in {
      bump("1-rc1") must_== "1"
      bump("1.2-rc1") must_== "1.2"
      bump("1.2.3-rc1") must_== "1.2.3"

      bump("1-rc") must_== "1"
      bump("1-RC1") must_== "1"
      bump("1-M1") must_== "1"
      bump("1-rc-1") must_== "1"
      bump("1-beta") must_== "1"
      bump("1-beta-1") must_== "1"
      bump("1-alpha") must_== "1"
    }
    "not drop the qualifier if it's not a pre release" in {
      bump("1.2.3-Final") must_== "1.2.4-Final"
    }
  }

}
