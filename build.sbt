organization := "com.github.gseitz"

name := "sbt-release"

version := "0.8.5-SNAPSHOT"

sbtPlugin := true

publishTo <<= (version) { version: String =>
   val scalasbt = "http://scalasbt.artifactoryonline.com/scalasbt/"
   val (name, url) = if (version.contains("-SNAPSHOT"))
                       ("sbt-plugin-snapshots-publish", scalasbt+"sbt-plugin-snapshots")
                     else
                       ("sbt-plugin-releases-publish", scalasbt+"sbt-plugin-releases")
   Some(Resolver.url(name, new URL(url))(Resolver.ivyStylePatterns))
}

publishMavenStyle := false

scalacOptions += "-deprecation"

crossBuildingSettings

CrossBuilding.crossSbtVersions := Seq("0.11.3", "0.12", "0.13")

CrossBuilding.scriptedSettings

scriptedLaunchOpts <<= (scriptedLaunchOpts, version) { case (s,v) => s ++
  Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + v)
}

scriptedBufferLog := false
