lazy val `sbt-release` = project in file(".")

organization := "com.github.sbt"
name := "sbt-release"

crossScalaVersions += "3.8.2"

pluginCrossBuild / sbtVersion := {
  scalaBinaryVersion.value match {
    case "2.12" =>
      (pluginCrossBuild / sbtVersion).value
    case _ =>
      "2.0.0-RC9"
  }
}

homepage := Some(url("https://github.com/sbt/sbt-release"))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

publishMavenStyle := true
scalacOptions ++= Seq("-deprecation", "-feature", "-language:implicitConversions")

scalacOptions ++= {
  scalaBinaryVersion.value match {
    case "3" =>
      Nil
    case _ =>
      Seq("-release:8")
  }
}

val unusedWarnings = Def.setting(
  scalaBinaryVersion.value match {
    case "2.12" =>
      Seq("-Ywarn-unused:imports")
    case _ =>
      Seq(
        "-Wunused:imports",
        "-Wconf:msg=is no longer supported for vararg splices:error",
      )
  }
)

scalacOptions ++= unusedWarnings.value

Seq(Compile, Test).flatMap(c => c / console / scalacOptions --= unusedWarnings.value)

def hash(): String = sys.process.Process("git rev-parse HEAD").lineStream_!.head

Compile / doc / scalacOptions ++= {
  Seq(
    "-sourcepath",
    (LocalRootProject / baseDirectory).value.getAbsolutePath,
    "-doc-source-url",
    s"https://github.com/sbt/sbt-release/tree/${hash()}€{FILE_PATH}.scala"
  )
}

libraryDependencies ++= Seq("org.specs2" %% "specs2-core" % "4.23.0" % "test")

// Scripted
enablePlugins(SbtPlugin)
scriptedLaunchOpts := {
  scriptedLaunchOpts.value ++ Seq(
    "-Xmx1024M",
    "-Dsbt.build.onchange=warn",
    "-Dplugin.version=" + version.value
  )
}
scriptedBufferLog := false

pomExtra := (
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
)
