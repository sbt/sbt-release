import sbtrelease.ReleaseStateTransformations._

val Scala211 = "2.11.12"

scalaVersion := Scala211

crossScalaVersions := Scala211 :: "2.10.7" :: Nil

releaseCrossBuild := false

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

releaseProcess := Seq(
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  crossRunTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  setNextVersion,
  commitNextVersion
)
