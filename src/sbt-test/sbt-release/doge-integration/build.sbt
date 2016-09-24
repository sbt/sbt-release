import sbt.complete.DefaultParsers._
import sbtrelease.ReleaseStateTransformations._

val Scala210 = "2.10.6"

val SupportedScalaVersions = Seq(Scala210, "2.11.8")

val artifactDir = new File("artifacts")

val commonSettings = Seq(
  scalaVersion := Scala210,
  publishTo := Some(Resolver.file("file", artifactDir)),
  releaseCrossBuild := false,
  releaseIgnoreUntrackedFiles := true,
  releaseProcess := Seq[ReleaseStep](
    releaseStepCommand("+publish")
  )
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false)
  .aggregate(library, plugin)
  .enablePlugins(CrossPerProjectPlugin)

// since it's a library it should be cross published
val library = (project in file("library"))
  .settings(commonSettings: _*)
  .settings(crossScalaVersions := SupportedScalaVersions)

// since it's an sbt plugin, it should only be published for 2.10
val plugin = (project in file("plugin"))
  .settings(commonSettings: _*)
  .settings(sbtPlugin := true, crossScalaVersions := Seq(Scala210))
  .dependsOn(library)

val assertArtifactExists = inputKey[Unit]("Check that an artifact with a given name and Scala version exists")

val argParser = Space ~> StringBasic

assertArtifactExists := {
  val (nameArg, scalaVersionArg) = (argParser ~ argParser).parsed
  val expectedDir = new File(artifactDir, s"$nameArg/${nameArg}_${scalaVersionArg}/${version.value}")
  val expectedFile = new File(expectedDir, s"${nameArg}_${scalaVersionArg}-${version.value}.jar")
  if (!expectedFile.exists) {
    error(s"$expectedFile does not exist")
  }
}

val assertPluginArtifactExists = inputKey[Unit]("Check that an artifact with a given name, Scala version, and sbt version exists")

assertPluginArtifactExists := {
  val ((nameArg, scalaVersionArg), sbtVersionArg) = (argParser ~ argParser ~ argParser).parsed
  val expectedDir = new File(artifactDir, s"$nameArg/${nameArg}_${scalaVersionArg}_${sbtVersionArg}/${version.value}")
  val expectedFile = new File(expectedDir, s"${nameArg}-${version.value}.jar")
    if (!expectedFile.exists) {
    error(s"$expectedFile does not exist")
  }
}
