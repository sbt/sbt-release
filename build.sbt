organization := "com.github.gseitz"

name := "sbt-release"

version := "0.5-SNAPSHOT"

sbtPlugin := true

publishTo := Some(Resolver.file("gseitz@github", file(Path.userHome + "/dev/repo")))
