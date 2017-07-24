import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](runTest, FailTest.createFile)

scalaVersion := "2.10.3"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.3" % "test"
