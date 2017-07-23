lazy val `sbt-release` = project in file(".")

organization := "com.github.gseitz"
name := "sbt-release"

homepage := Some(url("https://github.com/sbt/sbt-release"))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

crossSbtVersions := Vector("0.13.15", "1.0.0-M6")
sbtPlugin := true
publishMavenStyle := false
scalacOptions += "-deprecation"

libraryDependencies ++= Seq("org.specs2" %% "specs2-core" % "3.9.4" % "test")
resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Scripted

// workaround from https://github.com/sbt/sbt/issues/3325
scriptedSettings.filterNot(_.key.key.label == libraryDependencies.key.label)


libraryDependencies ++= {
  CrossVersion.binarySbtVersion(scriptedSbt.value) match {
    case "0.13" =>
      Seq(
        "org.scala-sbt" % "scripted-sbt" % scriptedSbt.value % scriptedConf.toString,
        "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % scriptedLaunchConf.toString
      )
    case _ =>
      Seq(
        "org.scala-sbt" %% "scripted-sbt" % scriptedSbt.value % scriptedConf.toString,
        "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % scriptedLaunchConf.toString
      )
  }
}

scriptedLaunchOpts := {
  Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value)
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