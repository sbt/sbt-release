import sbtrelease.ReleaseStateTransformations._

scalaVersion := "2.13.17"

libraryDependencies += "org.scalatest" %% "scalatest-funspec" % "3.2.19" % "test"

releaseProcess := Seq(
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  setNextVersion,
  commitNextVersion
)
