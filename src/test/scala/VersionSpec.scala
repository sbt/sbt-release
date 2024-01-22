package sbtrelease

import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification

object VersionSpec extends Specification {

  def version(v: String) = Version(v) match {
    case Some(parsed) => parsed
    case None => sys.error("Can't parse version " + v)
  }
  "Next Version bumping" should {
    def testBumpNext(input: String, expectedOutput: String): MatchResult[Any] = version(input).bumpNext.unapply must_== expectedOutput

    def testBumpNextStable(input: String, expectedOutput: String): MatchResult[Any] = version(input).bumpNextStable.unapply must_== expectedOutput

    def testBothBumpNextStrategies(input: String, expectedOutput: String): MatchResult[Any] = {
      testBumpNext(input, expectedOutput)
      testBumpNextStable(input, expectedOutput)
    }

    "bump the major version if there's only a major version" in {
      testBothBumpNextStrategies("1", "2")
    }
    "bump the minor version if there's only a minor version" in {
      testBothBumpNextStrategies("1.2", "1.3")
    }
    "bump the bugfix version if there's only a bugfix version" in {
      testBothBumpNextStrategies("1.2.3", "1.2.4")
    }
    "bump the nano version if there's only a nano version" in {
      testBothBumpNextStrategies("1.2.3.4", "1.2.3.5")
    }
    "drop the qualifier if it's a pre release and there is no version number at the end" in {
      testBothBumpNextStrategies("1-rc", "1")
      testBothBumpNextStrategies("1.0-rc", "1.0")
      testBothBumpNextStrategies("1.0.0-rc", "1.0.0")
      testBothBumpNextStrategies("1.0.0.0-rc", "1.0.0.0")
      testBothBumpNextStrategies("1-beta", "1")
      testBothBumpNextStrategies("1-alpha", "1")
    }
    "when the qualifier includes a pre release with a version number at the end" >> {
      "and Next is the bumping strategy" >> {
        "should bump the qualifier" in {
          testBumpNext("1-rc1", "1-rc2")
          testBumpNext("1.2-rc1", "1.2-rc2")
          testBumpNext("1.2.3-rc1", "1.2.3-rc2")
          testBumpNext("1-RC1", "1-RC2")
          testBumpNext("1-M1", "1-M2")
          testBumpNext("1-rc-1", "1-rc-2")
          testBumpNext("1-rc.1", "1-rc.2")
          testBumpNext("1-beta-1", "1-beta-2")
          testBumpNext("1-beta.1", "1-beta.2")
        }
      }
      "and NextStable is the bumping strategy" >> {
        "should remove the qualifier" in {
          testBumpNextStable("1-rc1", "1")
          testBumpNextStable("1.2-rc1", "1.2")
          testBumpNextStable("1.2.3-rc1", "1.2.3")
          testBumpNextStable("1-RC1", "1")
          testBumpNextStable("1-M1", "1")
          testBumpNextStable("1-rc-1", "1")
          testBumpNextStable("1-rc.1", "1")
          testBumpNextStable("1-beta-1", "1")
          testBumpNextStable("1-beta.1", "1")
        }
      }
    }
    "never drop the qualifier if it's a final release" >> {
      "when release is major" in {
        testBothBumpNextStrategies("1-Final", "2-Final")
      }
      "when release is minor" in {
        testBothBumpNextStrategies("1.2-Final", "1.3-Final")
      }
      "when release is subversion" in {
        testBothBumpNextStrategies("1.2.3-Final", "1.2.4-Final")
      }
      "when release is nano" in {
        testBothBumpNextStrategies("1.2.3.4-Final", "1.2.3.5-Final")
      }
    }
  }

  "Major Version bumping" should {
    def bumpMajor(v: String) = version(v).bumpMajor.unapply

    "bump the major version and reset other versions" in {
      bumpMajor("1.2.3.4.5") must_== "2.0.0.0.0"
    }
    "not drop the qualifier" in {
      bumpMajor("1.2.3.4.5-alpha") must_== "2.0.0.0.0-alpha"
    }
  }

  "Minor Version bumping" should {
    def bumpMinor(v: String) = version(v).bumpMinor.unapply

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
    def bumpSubversion(v: String)(i: Int) = version(v).maybeBumpSubversion(i).unapply

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
  "#isSnapshot" should {
    "return true when -SNAPSHOT is appended with another qualifier" in {
      version("1.0.0-RC1-SNAPSHOT").isSnapshot must_== true
    }
    "return false when -SNAPSHOT is not appended but another qualifier exists" in {
      version("1.0.0-RC1").isSnapshot must_== false
    }
    "return false when neither -SNAPSHOT nor qualifier are appended" in {
      version("1.0.0").isSnapshot must_== false
    }
  }
  "#asSnapshot" should {
    def snapshot(v: String) = version(v).asSnapshot.unapply
    "include qualifier if it exists" in {
      snapshot("1.0.0-RC1") must_== "1.0.0-RC1-SNAPSHOT"
    }
    "have no qualifier if none exists" in {
      snapshot("1.0.0") must_== "1.0.0-SNAPSHOT"
    }
  }
  "#withoutSnapshot" should {
    "remove the snapshot normally" in {
      version("1.0.0-SNAPSHOT").withoutSnapshot.unapply must_== "1.0.0"
    }
    "remove the snapshot without removing the qualifier" in {
      version("1.0.0-RC1-SNAPSHOT").withoutSnapshot.unapply must_== "1.0.0-RC1"
    }
  }
}
