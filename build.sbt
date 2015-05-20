lazy val `sbt-release` = project in file(".")

organization := "com.github.gseitz"
name := "sbt-release"

homepage := Some(url("https://github.com/sbt/sbt-release"))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

sbtPlugin := true
publishMavenStyle := false
scalacOptions += "-deprecation"

libraryDependencies ++= Seq("org.specs2" %% "specs2-core" % "3.6" % "test")
resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Scripted
scriptedSettings
scriptedLaunchOpts <<= (scriptedLaunchOpts, version) { case (s,v) => s ++
  Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + v)
}
scriptedBufferLog := false

// Bintray
bintrayOrganization := Some("sbt")
bintrayRepository := "sbt-plugin-releases"
bintrayPackage := "sbt-release"
bintrayReleaseOnPublish := false

// Release
import ReleaseTransformations._
releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  releaseStepTask(bintrayRelease in `sbt-release`),
  setNextVersion,
  commitNextVersion,
  pushChanges
)