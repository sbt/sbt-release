package sbtrelease

import sbt.*
import sbt.Keys.*
import sbtrelease.ReleasePlugin.autoImport.ReleaseStep
import sbtrelease.ReleasePlugin.autoImport.releaseStepCommandAndRemaining

private[sbtrelease] object ReleasePluginCompat {
  def testTask: TaskKey[?] = sbt.Keys.testFull

  val runClean: ReleaseStep = releaseStepCommandAndRemaining(BasicCommandStrings.CleanFull)

  val moduleIds: Def.Initialize[Task[Seq[ModuleID]]] = Def.task(
    (Runtime / managedClasspath).value.flatMap(_.get(Keys.moduleIDStr)).map(Classpaths.moduleIdJsonKeyFormat.read)
  )
}
