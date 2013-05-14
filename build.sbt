organization := "com.github.gseitz"

name := "sbt-release"

version := "0.8-BBSNAPSHOT"

unmanagedSourceDirectories in Compile <+= (sbtVersion, sourceDirectory in Compile) ((sv, sd) => new File(sd, "scala-sbt-" + sv))

sbtPlugin := true


publishTo := Some("backuity" at "http://dev.backuity.com:8081/nexus/content/repositories/releases/")

credentials += Credentials("Sonatype Nexus Repository Manager", "dev.backuity.com", "developer", "b@ckuit1")
