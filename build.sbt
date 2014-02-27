organization := "com.github.gseitz"

name := "sbt-release"

version := "0.8.2"

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
