organization := "com.github.gseitz"

name := "sbt-release"

version := "0.7"

unmanagedSourceDirectories in Compile <+= (sbtVersion, sourceDirectory in Compile) ((sv, sd) => new File(sd, "scala-sbt-" + sv))

sbtPlugin := true

publishTo <<= (version) { version: String =>
   val scalasbt = "http://scalasbt.artifactoryonline.com/scalasbt/"
   val (name, url) = if (version.contains("-SNAPSHOT"))
                       ("sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
                     else
                       ("sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
   Some(Resolver.url(name, new URL(url))(Resolver.ivyStylePatterns))
}

publishMavenStyle := false
