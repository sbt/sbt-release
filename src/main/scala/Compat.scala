package sbtrelease

import sbt._
import sbt.Def.ScopedKey
import sbt.EvaluateTask.{extractedTaskConfig, nodeView, runTask, withStreams}
import sbt.Keys._
import sbt.internal.Aggregation.KeyValue
import sbt.internal.{Act, Aggregation, ExtendableKeyIndex}
import sbt.std.Transform.DummyTaskMap
import scala.language.reflectiveCalls

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
    val toRun = tasks.map { case KeyValue(k,t) => t.map(v => KeyValue(k,v)) }.join
    val roots = tasks.map { case KeyValue(k,_) => k }


    val (newS, result) = withStreams(extracted.structure, state){ str =>
      val transform = nodeView(state, str, roots, extra)
      runTask(toRun, state,str, extracted.structure.index.triggers, config)(transform)
    }
    (newS, result)
  }

  def projectScope(project: Reference): Scope = Scope(Select(project), Zero, Zero, Zero)

  // checking if publishTo is configured
  def checkPublishTo(st: State): State = {
    // getPublishTo fails if no publish repository is set up for projects with `skip in publish := false`.
    val ex = st.extract
    val ref = ex.get(thisProjectRef)
    val (_, skipPublish) = ex.runTask(ref / publish / skip, st)
    if (!skipPublish) {
      Classpaths.getPublishTo(ex.runTask(ref / (Global / publishTo), st)._2)
    }
    st
  }

  val FailureCommand = sbt.Exec("--failure--", None, None)

  def excludeKeys(keys: Set[AttributeKey[_]]): Setting[_] => Boolean =
    _.key match {
      case ScopedKey(Scope(_, Zero, Zero, _), key) if keys.contains(key) => true
      case _ => false
    }

  def crossVersions(st: State): Seq[String] = {
    // copied from https://github.com/sbt/sbt/blob/2d7ec47b13e02526174f897cca0aef585bd7b128/main/src/main/scala/sbt/Cross.scala#L40
    val proj = Project.extract(st)
    import proj._
    crossVersions(proj, currentRef)
  }

  private def crossVersions(extracted: Extracted, proj: ProjectRef): Seq[String] = {
    import extracted._
    ((proj / crossScalaVersions) get structure.data) getOrElse {
      // reading scalaVersion is a one-time deal
      ((proj / scalaVersion) get structure.data).toSeq
    }
  }

  type Command = sbt.Exec
  implicit def command2String(command: Command) = command.commandLine
  implicit def string2Exex(s: String): Command = sbt.Exec(s, None, None)

  // type aliases
  type StructureIndex = sbt.internal.StructureIndex
  type BuildStructure = sbt.internal.BuildStructure
  val BuildStreams = sbt.internal.BuildStreams
  type BuildUtil[Proj] = sbt.internal.BuildUtil[Proj]
  val BuildUtil = sbt.internal.BuildUtil
  val Index = sbt.internal.Index
  type KeyIndex = sbt.internal.KeyIndex
  val KeyIndex = sbt.internal.KeyIndex
  type LoadedBuildUnit = sbt.internal.LoadedBuildUnit

  // https://github.com/sbt/sbt/issues/3792
  private[sbtrelease] def keyIndexApply(
    known: Iterable[ScopedKey[_]],
    projects: Map[URI, Set[String]],
    configurations: Map[String, Seq[Configuration]]
  ): ExtendableKeyIndex = try {
    // for sbt 1.1
    KeyIndex.asInstanceOf[{
      def apply(
        known: Iterable[ScopedKey[_]],
        projects: Map[URI, Set[String]],
        configurations: Map[String, Seq[Configuration]]
      ): ExtendableKeyIndex
    }].apply(known = known, projects = projects, configurations = configurations)
  } catch {
    case _: NoSuchMethodException =>
      // for sbt 1.0.x
      KeyIndex.asInstanceOf[{
        def apply(
          known: Iterable[ScopedKey[_]],
          projects: Map[URI, Set[String]],
        ): ExtendableKeyIndex
      }].apply(known = known, projects = projects)
  }

  // https://github.com/sbt/sbt/issues/3792
  private[sbtrelease] def keyIndexAggregate(known: Iterable[ScopedKey[_]],
                extra: BuildUtil[_],
                projects: Map[URI, Set[String]],
                configurations: Map[String, Seq[Configuration]]) = try {
    // for sbt 1.1
    KeyIndex.asInstanceOf[{
      def aggregate(
        known: Iterable[ScopedKey[_]],
        extra: BuildUtil[_],
        projects: Map[URI, Set[String]],
        configurations: Map[String, Seq[Configuration]]
      ): ExtendableKeyIndex
    }].aggregate(known = known, extra = extra, projects = projects, configurations = configurations)
  } catch {
    case _: NoSuchMethodException =>
      // for sbt 1.0.x
      KeyIndex.asInstanceOf[{
        def aggregate(
          known: Iterable[ScopedKey[_]],
          extra: BuildUtil[_],
          projects: Map[URI, Set[String]]
        ): ExtendableKeyIndex
      }].aggregate(known = known, extra = extra, projects = projects)
  }

}
