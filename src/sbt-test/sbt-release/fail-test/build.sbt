import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](runTest, FailTest.createFile)

scalaVersion := "2.10.7"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.1.0" % "test"
