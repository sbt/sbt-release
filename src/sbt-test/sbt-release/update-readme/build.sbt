import sbtrelease.ReleaseStateTransformations._


releaseProcess := Seq(
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  updateReadme,
  commitReadme,
  setNextVersion,
  commitNextVersion
)

TaskKey[Unit]("checkReadme") := {
  val readmeFile = baseDirectory.value / "README.md"
  val content = IO.read(readmeFile)

  assert(content ==
    """|# A project
       |
       |Add library in your `build.sbt`
       |
       |```scala
       |libraryDependencies += "com.example" %% "lib" % "2.3.4"
       |```
       |""".stripMargin, s"Readme wasn't updated correctly\n\n$content")

}