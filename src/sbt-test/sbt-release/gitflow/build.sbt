import sbtrelease.ReleaseStateTransformations._

releaseProcess := Seq(
  checkSnapshotDependencies,
  inquireVersions,
  inquireBranches,
  setReleaseVersion,
  commitReleaseVersion,
  setReleaseBranch,
  pushChanges,
  setNextBranch,
  setNextVersion,
  commitNextVersion,
  pushChanges
)