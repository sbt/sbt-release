import sbt._

package object sbtrelease {
  type Versions = (String, String)

  @deprecated("Use ReleaseStep", since = "0.5")
  type ReleasePart = ReleaseStep

  def releaseTask[T](key: TaskKey[T]) = { st: State =>
    Project.extract(st).runAggregated(key, st)
  }

  def versionFormatError = sys.error("Version format is not compatible with " + Version.VersionR.pattern.toString)
}
