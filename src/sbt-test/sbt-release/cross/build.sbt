import sbtrelease.ReleaseStateTransformations._

val Scala213 = "2.13.16"
val Scala212 = "2.13.17"

scalaVersion := Scala213

crossScalaVersions := Scala213 :: Scala212 :: Nil

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

name := "sbt-release-cross-test"

InputKey[Unit]("checkTargetDir") := {
  import complete.DefaultParsers._
  val args = spaceDelimited("<arg>").parsed
  val exists = args(1) match {
    case "exists" =>
      true
    case "not-exists" =>
      false
  }
  val dir = file {
    if (sbtVersion.value.startsWith("1")) {
      val scalaBinaryV = args(0)
      s"target/scala-${scalaBinaryV}/classes"
    } else {
      val scalaV = args(0) match {
        case "2.12" =>
          Scala212
        case "2.13" =>
          Scala213
      }
      s"target/out/jvm/scala-${scalaV}/sbt-release-cross-test/classes"
    }
  }

  assert(dir.isDirectory == exists)
}
