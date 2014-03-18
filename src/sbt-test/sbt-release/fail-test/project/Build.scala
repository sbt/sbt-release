import sbt._
import sbtrelease._

object FailTest {
  val createFile: ReleaseStep = { st: State =>
    IO.touch(file("file"))
    st
  }
}
