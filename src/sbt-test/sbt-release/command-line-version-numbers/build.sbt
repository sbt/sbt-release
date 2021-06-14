import ReleaseTransformations._
import sbt.complete.DefaultParsers._

name := "command-line-version-numbers"

publishTo := Some(Resolver.file("file",  new File( "." )) )

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  publishArtifacts,
  setNextVersion
)
scalaVersion := "2.10.7"


val checkContentsOfVersionSbt = inputKey[Unit]("Check that the contents of version.sbt is as expected")
val parser = Space ~> StringBasic

checkContentsOfVersionSbt := {
  val expected = parser.parsed
      val versionFile = ((baseDirectory).value) / "version.sbt"
      assert(IO.read(versionFile).contains(expected), s"does not contains ${expected} in ${versionFile}")
}


