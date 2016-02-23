import sbtrelease.ReleaseStateTransformations._

val Scala211 = "2.11.7"

scalaVersion := Scala211

crossScalaVersions := Scala211 :: "2.10.6" :: Nil

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
