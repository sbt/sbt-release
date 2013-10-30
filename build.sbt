organization := "com.github.gseitz"

name := "sbt-release"

version := "0.8-BBSNAPSHOT"

sbtPlugin := true

publishTo := Some("backuity" at "http://dev.backuity.com:8081/nexus/content/repositories/releases/")

credentials += Credentials("Sonatype Nexus Repository Manager", "dev.backuity.com", "developer", "b@ckuit1")

crossBuildingSettings

CrossBuilding.crossSbtVersions := Seq("0.11.3", "0.12", "0.13")
