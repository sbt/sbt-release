import sbtrelease._
import ReleaseKeys._
import ReleaseStateTransformations._

releaseSettings

releaseProcess := Seq[ReleaseStep](runTest, FailTest.createFile)

scalaVersion := "2.10.3"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.1.0" % "test"
