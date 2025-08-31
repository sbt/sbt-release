import sbtrelease.ReleaseStateTransformations._

val Scala213 = "2.13.16"

scalaVersion := Scala213

crossScalaVersions := Scala213 :: "2.12.20" :: Nil

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
