import sbt._
import sbtrelease.ReleasePlugin.autoImport._

object FailTest {
  val createFile: ReleaseStep = { st: State =>
    IO.touch(file("file"))
    st
  }
}
