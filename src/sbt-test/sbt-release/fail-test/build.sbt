import ReleaseTransformations._

val createFile: ReleaseStep = { (st: State) =>
  IO.touch(file("file"))
  st
}

releaseProcess := Seq[ReleaseStep](runTest, createFile)

scalaVersion := "2.13.17"

libraryDependencies += "org.scalatest" %% "scalatest-flatspec" % "3.2.19" % "test"
