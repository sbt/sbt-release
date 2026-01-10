package sbtrelease

import sbt.*
import sbt.Keys.*
import sbtrelease.ReleasePlugin.autoImport.ReleaseStep

private[sbtrelease] object ReleasePluginCompat {
  def testTask: TaskKey[?] = sbt.Keys.test

  val runClean: ReleaseStep = ReleaseStep(
    action = { st =>
      val extracted = Project.extract(st)
      val ref = extracted.get(thisProjectRef)
      extracted.runAggregated(ref / (Global / clean), st)
    }
  )

  val moduleIds: Def.Initialize[Task[Seq[ModuleID]]] = Def.task(
    (Runtime / managedClasspath).value.flatMap(_.get(moduleID.key))
  )
}
