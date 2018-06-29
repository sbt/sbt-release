// import sbtrelease.ReleaseStateTransformations._
import scala.sys.process.Process

// credits for the test to: https://github.com/rossabaker/sbt-release-exit-code

publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))

val failingTask = taskKey[Unit]("A task that will fail")

failingTask :=  {throw new IllegalStateException("Meh")}

def checkExitCode(rp: String)(expected: Int) = {
  val releaseProcess =
    Process("sbt", Seq(s"-Dplugin.version=${System.getProperty("plugin.version")}",
      s"set releaseProcess := $rp",
      "release with-defaults")
    )

  val exitValue = releaseProcess.!

  println("plugin version: " + System.getProperty("plugin.version"))
  println(s"exit code is $exitValue and should be $expected in $rp")

  assert(exitValue == expected)

  ()
}

TaskKey[Unit]("checkExitCodes") := {
  checkExitCode("Seq(sbtrelease.ReleaseStateTransformations.runTest)")(1)

  checkExitCode("""Seq(releaseStepCommand("show version"))""")(0)

  checkExitCode("""Seq(sbtrelease.ReleaseStateTransformations.runTest, releaseStepCommand("show version"))""")(1)

  checkExitCode("""Seq(releaseStepCommandAndRemaining("show version"))""")(0)

  checkExitCode("""Seq(releaseStepCommandAndRemaining("failingTask"))""")(1)
}
