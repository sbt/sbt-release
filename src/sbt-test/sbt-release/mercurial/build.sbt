import sbtrelease.ReleaseStateTransformations._

scalaVersion := "2.13.16"

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

name := "sbt-release-test-mercurial"

InputKey[Unit]("check") := {
  val f = file(
    if (sbtVersion.value.startsWith("1")) {
      s"target/scala-${scalaBinaryVersion.value}/classes/B.class"
    } else {
      s"target/out/jvm/scala-${scalaVersion.value}/sbt-release-test-mercurial/classes/B.class"
    }
  )
  assert(f.isFile)
}
