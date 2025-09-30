import ReleaseTransformations._
import sbt.complete.DefaultParsers._

name := "command-line-version-numbers"

publishTo := Some(Resolver.file("file", file(".")))

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  publishArtifacts,
  setNextVersion
)

scalaVersion := "2.13.17"

val checkContentsOfVersionSbt = inputKey[Unit]("Check that the contents of version.sbt is as expected")
val parser = Space ~> StringBasic

checkContentsOfVersionSbt := {
  val expected = parser.parsed
  val versionFile = baseDirectory.value / "version.sbt"
  assert(IO.read(versionFile).contains(expected), s"does not contains ${expected} in ${versionFile}")
}

InputKey[Unit]("checkJarFile") := {
  val dir = file(
    if (sbtVersion.value.startsWith("1")) {
      s"target/scala-${scalaBinaryVersion.value}"
    } else {
      s"target/out/jvm/scala-${scalaVersion.value}/command-line-version-numbers"
    }
  )

  assert((dir / "command-line-version-numbers_2.13-36.14.3.jar").isFile)
}
