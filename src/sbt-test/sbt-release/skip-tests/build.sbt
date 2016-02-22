import sbtrelease.ReleaseStateTransformations._

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
