import sbtrelease.Release._

seq(releaseSettings: _*)

organization := "com.github.gseitz"

name := "sbt-release"

version := "0.3"

sbtPlugin := true

publishTo := Some(Resolver.file("gseitz@github", file(Path.userHome + "/dev/repo")))
