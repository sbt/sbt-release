import sbt.*
import sbtrelease.ReleasePlugin.autoImport.*

object FailTest {
  val createFile: ReleaseStep = { (st: State) =>
    IO.touch(file("file"))
    st
  }
}
