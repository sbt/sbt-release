import aether.Aether._

organization := "com.github.gseitz"

name := "sbt-release"

version := "0.8.4-SNAPSHOT"

sbtPlugin := true

//publishTo <<= (version) { version: String =>
//   val scalasbt = "http://scalasbt.artifactoryonline.com/scalasbt/"
//   val (name, url) = if (version.contains("-SNAPSHOT"))
//                       ("sbt-plugin-snapshots-publish", scalasbt+"sbt-plugin-snapshots")
//                     else
//                       ("sbt-plugin-releases-publish", scalasbt+"sbt-plugin-releases")
//   Some(Resolver.url(name, new URL(url))(Resolver.ivyStylePatterns))
//}

credentials += Credentials("kbcs-snapshots", "nexus-int.kbcsecurities.net", "deployment", "deployment123")

publishTo := {
  val nexus = "http://nexus-int.kbcsecurities.net:8081/nexus/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("kbcs-snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("kbcs-releases"  at nexus + "content/repositories/releases")
}

seq(aetherSettings: _*)

seq(aetherPublishSettings: _*)

publishMavenStyle := false

scalacOptions += "-deprecation"

crossBuildingSettings

CrossBuilding.crossSbtVersions := Seq("0.11.3", "0.12", "0.13")

CrossBuilding.scriptedSettings

scriptedLaunchOpts <<= (scriptedLaunchOpts, version) { case (s,v) => s ++
  Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + v)
}

scriptedBufferLog := false
