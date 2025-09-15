// import sbtrelease.ReleaseStateTransformations._
import scala.sys.process.Process

// credits for the test to: https://github.com/rossabaker/sbt-release-exit-code

publishTo := Some(Resolver.file("file", new File(Path.userHome.absolutePath + "/.m2/repository")))

val failingTask = taskKey[Unit]("A task that will fail")

failingTask := { throw new IllegalStateException("Meh") }
