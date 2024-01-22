import sbt.complete.DefaultParsers._
import sbtrelease.ReleaseStateTransformations._

releaseVersionFile := file("version.sbt")

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

val checkContentsOfVersionSbt = inputKey[Unit]("Check that the contents of version.sbt is as expected")
val parser = Space ~> StringBasic

checkContentsOfVersionSbt := {
  val expected = parser.parsed
  val versionFile = ((baseDirectory).value) / "version.sbt"
  assert(IO.read(versionFile).contains(expected), s"does not contains ${expected} in ${versionFile}")
}
