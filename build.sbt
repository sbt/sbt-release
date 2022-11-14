lazy val `sbt-release` = project in file(".")

organization := "com.github.sbt"
name := "sbt-release"

homepage := Some(url("https://github.com/sbt/sbt-release"))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

publishMavenStyle := true
scalacOptions ++= Seq("-deprecation", "-feature", "-language:implicitConversions")

val unusedWarnings = Seq("-Ywarn-unused:imports")

scalacOptions ++= unusedWarnings

Seq(Compile, Test).flatMap(c =>
  c / console / scalacOptions --= unusedWarnings
)

def hash(): String = sys.process.Process("git rev-parse HEAD").lineStream_!.head

Compile / doc / scalacOptions ++= {
  Seq(
    "-sourcepath", (LocalRootProject / baseDirectory).value.getAbsolutePath,
    "-doc-source-url", s"https://github.com/sbt/sbt-release/tree/${hash()}€{FILE_PATH}.scala"
  )
}

libraryDependencies ++= Seq("org.specs2" %% "specs2-core" % "4.19.0" % "test")

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
