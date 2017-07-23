package sbtrelease

import sbt.std.Transform.DummyTaskMap
import sbt.{EvaluateTask, Result, ScopeMask, State, TaskKey, Act, Aggregation}
import sbt.Aggregation.KeyValue
import sbt.Keys._
import sbt._
import sbt.Aggregation.KeyValue
import sbt.std.Transform.DummyTaskMap
import sbt.Keys._
import sbt.Package.ManifestAttributes
import annotation.tailrec
import ReleasePlugin.autoImport._
import ReleaseKeys._
import sbt.{Load => iLoad}

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

  val Load = iLoad
}