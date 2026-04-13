libraryDependencies += "com.github.xuwei-k" %% "scala-version-from-sbt-version" % "0.1.0"

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.2")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.0")
