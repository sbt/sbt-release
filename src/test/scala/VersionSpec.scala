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
    "bump the nano version if there's only a nano version" in {
      bump("1.2.3.4") must_== "1.2.3.5"
    }
    "drop the qualifier if it's a pre release" in {
      bump("1-rc1") must_== "1"
      bump("1.2-rc1") must_== "1.2"
      bump("1.2.3-rc1") must_== "1.2.3"

      bump("1-rc") must_== "1"
      bump("1-RC1") must_== "1"
      bump("1-M1") must_== "1"
      bump("1-rc-1") must_== "1"
      bump("1-rc.1") must_== "1"
      bump("1-beta") must_== "1"
      bump("1-beta-1") must_== "1"
      bump("1-beta.1") must_== "1"
      bump("1-alpha") must_== "1"
    }
    "not drop the qualifier if it's not a pre release" in {
      bump("1.2.3-Final") must_== "1.2.4-Final"
    }
    "not drop the post-nano qualifier if it's not a pre release" in {
      bump("1.2.3.4-Final") must_== "1.2.3.5-Final"
    }
  }

  "Major Version bumping" should {
    def bumpMajor(v: String) = version(v).bumpMajor.string

    "bump the major version and reset other versions" in {
      bumpMajor("1.2.3.4.5") must_== "2.0.0.0.0"
    }
    "not drop the qualifier" in {
      bumpMajor("1.2.3.4.5-alpha") must_== "2.0.0.0.0-alpha"
    }
  }

  "Minor Version bumping" should {
    def bumpMinor(v: String) = version(v).bumpMinor.string

    "bump the minor version" in {
      bumpMinor("1.2") must_== "1.3"
    }
    "bump the minor version and reset other subversions" in {
      bumpMinor("1.2.3.4.5") must_== "1.3.0.0.0"
    }
    "not bump the major version when no minor version" in {
      bumpMinor("1") must_== "1"
    }
    "not drop the qualifier" in {
      bumpMinor("1.2.3.4.5-alpha") must_== "1.3.0.0.0-alpha"
    }
  }

  "Subversion bumping" should {
    def bumpSubversion(v: String)(i: Int) = version(v).maybeBumpSubversion(i).string

    "bump the subversion" in {
      bumpSubversion("1.2")(0) must_== "1.3"
    }
    "bump the subversion and reset lower subversions" in {
      bumpSubversion("1.2.3.4.5")(0) must_== "1.3.0.0.0"
    }
    "not change anything with an invalid subversion index" in {
      bumpSubversion("1.2-beta")(1) must_== "1.2-beta"
    }
    "not drop the qualifier" in {
      bumpSubversion("1.2.3.4.5-alpha")(2) must_== "1.2.3.5.0-alpha"
    }
  }

}
