import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](runTest, FailTest.createFile)

scalaVersion := "2.13.16"

libraryDependencies += "org.scalatest" %% "scalatest-flatspec" % "3.2.19" % "test"
