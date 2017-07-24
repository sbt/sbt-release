import sbtrelease.ReleaseStateTransformations._

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.3" % "test"

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
