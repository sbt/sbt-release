organization := "com.github.gseitz"

name := "sbt-release"

version := "0.5-SNAPSHOT"

unmanagedSourceDirectories in Compile <+= (sbtVersion, sourceDirectory in Compile) ((sv, sd) => new File(sd, "scala_" + sv))

sbtPlugin := true

publishTo := Some(Resolver.file("gseitz@github", file(Path.userHome + "/dev/repo")))
