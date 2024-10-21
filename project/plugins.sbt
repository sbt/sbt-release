libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.0")

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.9.0")
