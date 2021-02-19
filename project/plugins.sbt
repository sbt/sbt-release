libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.5")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.2")

// This project is its own plugin :)
unmanagedSourceDirectories in Compile += baseDirectory.value.getParentFile / "src" / "main" / "scala"
unmanagedSourceDirectories in Compile += baseDirectory.value.getParentFile / "src" / "main" / "scala-sbt-1.0"
