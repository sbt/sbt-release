import ReleaseTransformations._

val common = Def.settings(
  scalaVersion := "2.13.18",
  libraryDependencies += "org.scalatest" %% "scalatest-flatspec" % "3.2.20" % "test",
)

common

val createFile: ReleaseStep = { (st: State) =>
  IO.touch(file("file"))
  st
}

releaseProcess := Seq[ReleaseStep](runTest, createFile)

lazy val a1 = project.settings(common)
lazy val a2 = project.settings(common)
