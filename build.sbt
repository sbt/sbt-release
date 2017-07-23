lazy val `sbt-release` = project in file(".")

organization := "com.github.gseitz"
name := "sbt-release"

homepage := Some(url("https://github.com/sbt/sbt-release"))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

crossSbtVersions := Vector("0.13.16", "1.0.0-RC3")
sbtPlugin := true
publishMavenStyle := false
scalacOptions += "-deprecation"

val unusedWarnings = Seq("-Ywarn-unused-import")

scalacOptions ++= PartialFunction.condOpt(CrossVersion.partialVersion(scalaVersion.value)){
  case Some((2, v)) if v >= 11 => unusedWarnings
}.toList.flatten

Seq(Compile, Test).flatMap(c =>
  scalacOptions in (c, console) --= unusedWarnings
)

libraryDependencies ++= Seq("org.specs2" %% "specs2-core" % "3.9.1" % "test")

// Scripted
scriptedSettings
scriptedLaunchOpts := {
  scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false

// Bintray
bintrayOrganization := Some("sbt")
bintrayRepository := "sbt-plugin-releases"
bintrayPackage := "sbt-release"
bintrayReleaseOnPublish := false

// Release
import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepCommandAndRemaining("^ test"),
  releaseStepCommandAndRemaining("^ scripted"),
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("^ publishSigned"),
  releaseStepTask(bintrayRelease in `sbt-release`),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
