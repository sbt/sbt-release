package sbtrelease

import sbt._
import sbt.Aggregation.KeyValue
import sbt.Def.ScopedKey
import sbt.Keys._
import sbt.Package.ManifestAttributes
import sbt.std.Transform.DummyTaskMap
import ReleasePlugin.autoImport._
import ReleaseKeys._

object Compat {

  import Utilities._

  def runTaskAggregated[T](taskKey: TaskKey[T], state: State): (State, Result[Seq[KeyValue[T]]]) = {
    import EvaluateTask._

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

  def projectScope(project: Reference): Scope = Scope(Select(project), Global, Global, Global)

  // checking if publishTo is configured
  def checkPublishTo(st: State ): State = {
    // getPublishTo fails if no publish repository is set up.
    val ex = st.extract
    val ref = ex.get(thisProjectRef)
    Classpaths.getPublishTo(ex.get(publishTo in Global in ref))
    st
  }

  val FailureCommand = "--failure--"

  def excludeKeys(keys: Set[AttributeKey[_]]): Setting[_] => Boolean =
    _.key match {
      case ScopedKey(Scope(_, Global, Global, _), key) if keys.contains(key) => true
      case _ => false
    }

  val crossVersions = Cross.crossVersions _

  type Command = String

  // type aliases
  type StructureIndex = sbt.StructureIndex
  type BuildStructure = sbt.BuildStructure
  val BuildStreams = sbt.BuildStreams
  type BuildUtil[Proj] = sbt.BuildUtil[Proj]
  val BuildUtil = sbt.BuildUtil
  val Index = sbt.Index
  type KeyIndex = sbt.KeyIndex
  val KeyIndex = sbt.KeyIndex
  type LoadedBuildUnit = sbt.LoadedBuildUnit

  private[sbtrelease] def keyIndexApply(
    known: Iterable[ScopedKey[_]],
    projects: Map[URI, Set[String]],
    configurations: Map[String, Seq[Configuration]]
  ) = {
    KeyIndex.apply(known = known, projects = projects)
  }

  private[sbtrelease] def keyIndexAggregate(known: Iterable[ScopedKey[_]],
                extra: BuildUtil[_],
                projects: Map[URI, Set[String]],
                configurations: Map[String, Seq[Configuration]]) = {
    KeyIndex.aggregate(known = known, extra = extra, projects = projects)
  }

  private[sbtrelease] implicit class ProcessBuilderOps(val self: scala.sys.process.ProcessBuilder) {
    def lineStream = self.lines
  }
}
