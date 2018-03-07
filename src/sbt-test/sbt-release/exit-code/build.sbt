// import sbtrelease.ReleaseStateTransformations._
import scala.sys.process.Process

// credits for the test to: https://github.com/rossabaker/sbt-release-exit-code

publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))

def checkExitCode(rp: String)(expected: Int) = {
  val releaseProcess =
    Process("sbt", Seq(s"-Dplugin.version=${System.getProperty("plugin.version")}",
      s"set releaseProcess := $rp",
      "release with-defaults")
    )

  val exitValue = releaseProcess.!

  println(s"exit code is $exitValue and should be $expected")

  assert(exitValue == expected)

  ()
}

TaskKey[Unit]("checkExitCodes") := {
  checkExitCode("Seq(sbtrelease.ReleaseStateTransformations.runTest)")(1)

  checkExitCode("""Seq(releaseStepCommand("show version"))""")(0)

  checkExitCode("""Seq(sbtrelease.ReleaseStateTransformations.runTest, releaseStepCommand("show version"))""")(1)
}
