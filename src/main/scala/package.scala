import sbt._

package object sbtrelease {
  type Versions = (String, String)
  type ReleasePart = State => State

  def releaseTask[T](key: TaskKey[T]): ReleasePart = { st =>
    Project.extract(st).runAggregated(key, st)
  }

  def versionFormatError = sys.error("Version format is not compatible with [0-9]+([0-9]+)?([0-9]+)?(-.*)?")
}
