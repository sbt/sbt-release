import sbtrelease.ReleaseStateTransformations._

releaseUseGlobalVersion := false

val subProject = project

releaseProcess := Seq(
  ReleaseStep { state =>
    val v = state.getSetting(subProject / version)
    assert(v == Some("0.1.0-SNAPSHOT"), v)
    state
  },
  inquireVersions,
  setReleaseVersion,
  ReleaseStep { state =>
    val v = state.getSetting(subProject / version)
    assert(v == Some("0.1.0"), v)
    state
  },
  setNextVersion,
  ReleaseStep { state =>
    val v = state.getSetting(subProject / version)
    assert(v == Some("0.1.1-SNAPSHOT"), v)
    state
  },
)
