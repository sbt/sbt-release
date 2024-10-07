package sbtrelease

import sbt.*
import sbt.Keys.*

private[sbtrelease] object ReleasePluginCompat {
  def testTask: TaskKey[?] = sbt.Keys.test

  val moduleIds: Def.Initialize[Task[Seq[ModuleID]]] = Def.task(
    (Runtime / managedClasspath).value.flatMap(_.get(moduleID.key))
  )
}
