import sbtrelease.ReleaseStateTransformations._

val Scala211 = "2.11.12"

scalaVersion := Scala211

crossScalaVersions := Scala211 :: "2.10.7" :: Nil

releaseCrossBuild := false

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
