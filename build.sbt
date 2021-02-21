lazy val `sbt-release` = project in file(".")

organization := "com.github.sbt"
name := "sbt-release"

homepage := Some(url("https://github.com/sbt/sbt-release"))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

// Don't update crossSbtVersions!
// https://github.com/sbt/sbt/issues/5049
crossSbtVersions := Vector("0.13.18", "1.1.6")
publishMavenStyle := false
scalacOptions ++= Seq("-deprecation", "-feature", "-language:implicitConversions")

val unusedWarnings = Seq("-Ywarn-unused:imports")

scalacOptions ++= PartialFunction.condOpt(CrossVersion.partialVersion(scalaVersion.value)){
  case Some((2, v)) if v >= 11 => unusedWarnings
}.toList.flatten

Seq(Compile, Test).flatMap(c =>
  scalacOptions in (c, console) --= unusedWarnings
)

def hash(): String = sys.process.Process("git rev-parse HEAD").lineStream_!.head

scalacOptions in (Compile, doc) ++= {
  Seq(
    "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
    "-doc-source-url", s"https://github.com/sbt/sbt-release/tree/${hash()}€{FILE_PATH}.scala"
  )
}

libraryDependencies ++= Seq("org.specs2" %% "specs2-core" % "3.10.0" % "test")

// Scripted
enablePlugins(SbtPlugin)
scriptedLaunchOpts := {
  scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false

pomExtra := {
  <developers>{
    Seq(
      ("xuwei-k", "Kenji Yoshida"),
    ).map { case (id, name) =>
      <developer>
        <id>{id}</id>
        <name>{name}</name>
        <url>https://github.com/{id}</url>
      </developer>
    }
  }</developers>
}

publishMavenStyle := true
