package sbt

import sbt.internal.BuildStructure

object LoadCompat {

  def transformSettings(
    thisScope: Scope,
    uri: URI,
    rootProject: URI => String,
    settings: Seq[Setting[?]]
  ): Seq[Setting[?]] =
    sbt.internal.Load.transformSettings(thisScope, uri, rootProject, settings)

  def reapply(
    newSettings: Seq[Setting[?]],
    structure: BuildStructure
  )(using display: Show[ScopedKey[?]]): BuildStructure =
    sbt.internal.Load.reapply(newSettings, structure)
}
