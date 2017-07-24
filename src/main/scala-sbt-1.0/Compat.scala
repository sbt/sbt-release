package sbtrelease

import sbt.internal.Aggregation.KeyValue
import sbt.EvaluateTask.{extractedTaskConfig, nodeView, runTask, withStreams}
import sbt.internal.{Act, Aggregation}
import sbt.internal.Aggregation.{KeyValue}
import sbt.internal.{Load => iLoad}
import sbt.std.Transform.DummyTaskMap
import sbt.{State, Result, TaskKey, EvaluateTask, ScopeMask, Scope, Reference, Select, Zero}

object Compat {
  def runTaskAggregated[T](taskKey: TaskKey[T], state: State): (State, Result[Seq[KeyValue[T]]]) = {
    import EvaluateTask._
    import Utilities._

    val extra = DummyTaskMap(Nil)
    val extracted = state.extract
    val config = extractedTaskConfig(extracted, extracted.structure, state)

    val rkey = Utilities.resolve(taskKey.scopedKey, extracted)
    val keys = Aggregation.aggregate(rkey, ScopeMask(), extracted.structure.extra)
    val tasks = Act.keyValues(extracted.structure)(keys)
    val toRun = tasks map { case KeyValue(k,t) => t.map(v => KeyValue(k,v)) } join
    val roots = tasks map { case KeyValue(k,_) => k }


    val (newS, result) = withStreams(extracted.structure, state){ str =>
      val transform = nodeView(state, str, roots, extra)
      runTask(toRun, state,str, extracted.structure.index.triggers, config)(transform)
    }
    (newS, result)
  }

  def projectScope(project: Reference): Scope = Scope(Select(project), Zero, Zero, Zero)

  type StructureIndex = sbt.internal.StructureIndex
  type BuildStructure = sbt.internal.BuildStructure
  val BuildStreams = sbt.internal.BuildStreams
  type BuildUtil[Proj] = sbt.internal.BuildUtil[Proj]
  val BuildUtil = sbt.internal.BuildUtil
  val Index = sbt.internal.Index
  type KeyIndex = sbt.internal.KeyIndex
  val KeyIndex = sbt.internal.KeyIndex
  type LoadedBuildUnit = sbt.internal.LoadedBuildUnit
}