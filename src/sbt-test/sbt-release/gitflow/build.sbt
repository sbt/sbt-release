import ReleaseTransformations._

releaseProcess := Seq(
  checkSnapshotDependencies,
  inquireVersions,
  inquireBranches,
  setReleaseVersion,
  commitReleaseVersion,
  setReleaseBranch,
  setNextBranch,
  setNextVersion,
  commitNextVersion
)