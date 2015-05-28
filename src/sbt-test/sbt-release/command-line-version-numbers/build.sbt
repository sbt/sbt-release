import ReleaseTransformations._
import sbt.complete.DefaultParsers._

publishTo := Some(Resolver.file("file",  new File( "." )) )

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  //commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  //tagRelease,                             // : ReleaseStep
  publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
  setNextVersion//,                         // : ReleaseStep
  //commitNextVersion//,                      // : ReleaseStep
  //pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
)
scalaVersion := "2.10.3"


val checkContentsOfVersionSbt = inputKey[Unit]("Check that the contents of version.sbt is as expected")
val parser = Space ~> StringBasic

checkContentsOfVersionSbt := {
  val expected = parser.parsed
      val versionFile = ((baseDirectory).value) / "version.sbt"
      assert(IO.read(versionFile).contains(expected), s"does not contains ${expected} in ${versionFile}")
}


