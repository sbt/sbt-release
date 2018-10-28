package object sbtrelease {
  type Versions = (String, String)

  def versionFormatError(version: String) = sys.error(s"Version [$version] format is not compatible with " + Version.VersionR.pattern.toString)
}
