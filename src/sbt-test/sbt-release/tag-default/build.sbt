import sbtrelease.ReleaseStateTransformations._

scalaVersion := "2.11.12"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

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
