import sbt.{Project, ScopedTask, State}

package object sbtrelease {
  type Versions = (String, String)
  type ReleasePart = State => State

  def releaseTask[T](key: ScopedTask[T]): ReleasePart = { st =>
    Project.extract(st).evalTask(key, st)
    st
  }

  def versionFormatError = sys.error("Version format is not compatible with [0-9]+([0-9]+)?([0-9]+)?(-.*)?")
}
