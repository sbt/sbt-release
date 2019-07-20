package sbtrelease

import sbt._
import sbt.Keys.{resolvedScoped, streams}
import sbt.Def.ScopedKey

// sbt.Load was made private in sbt 1.0
// the core developers recommend copying the required methods: https://github.com/sbt/sbt/issues/3296#issuecomment-315218050
object Load {

  import Compat._

  def transformSettings(thisScope: Scope, uri: URI, rootProject: URI => String, settings: Seq[Setting[_]]): Seq[Setting[_]] =
    Project.transform(Scope.resolveScope(thisScope, uri, rootProject), settings)
  // Reevaluates settings after modifying them.  Does not recompile or reload any build components.
  def reapply(newSettings: Seq[Setting[_]], structure: BuildStructure)(implicit display: Show[ScopedKey[_]]): BuildStructure =
  {
    val transformed = finalTransforms(newSettings)
    val newData = Def.make(transformed)(structure.delegates, structure.scopeLocal, display)
    val newIndex = structureIndex(newData, transformed, index => BuildUtil(structure.root, structure.units, index, newData), structure.units)
    val newStreams = BuildStreams.mkStreams(structure.units, structure.root, newData)
    new BuildStructure(units = structure.units, root = structure.root, settings = transformed, data = newData, index = newIndex, streams = newStreams, delegates = structure.delegates, scopeLocal = structure.scopeLocal)
  }

  // map dependencies on the special tasks:
  // 1. the scope of 'streams' is the same as the defining key and has the task axis set to the defining key
  // 2. the defining key is stored on constructed tasks: used for error reporting among other things
  // 3. resolvedScoped is replaced with the defining key as a value
  // Note: this must be idempotent.
  def finalTransforms(ss: Seq[Setting[_]]): Seq[Setting[_]] =
  {
    def mapSpecial(to: ScopedKey[_]) = new (ScopedKey ~> ScopedKey) {
      def apply[T](key: ScopedKey[T]) =
        if (key.key == streams.key)
          ScopedKey(Scope.fillTaskAxis(Scope.replaceThis(to.scope)(key.scope), to.key), key.key)
        else key
    }
    def setDefining[T] = (key: ScopedKey[T], value: T) => value match {
      case tk: Task[t]      => setDefinitionKey(tk, key).asInstanceOf[T]
      case ik: InputTask[t] => ik.mapTask(tk => setDefinitionKey(tk, key)).asInstanceOf[T]
      case _                => value
    }
    def setResolved(defining: ScopedKey[_]) = new (ScopedKey ~> Option) {
      def apply[T](key: ScopedKey[T]): Option[T] =
        key.key match {
          case resolvedScoped.key => Some(defining.asInstanceOf[T])
          case _                  => None
        }
    }
    ss.map(s => s mapConstant setResolved(s.key) mapReferenced mapSpecial(s.key) mapInit setDefining)
  }
  def structureIndex(data: Settings[Scope], settings: Seq[Setting[_]], extra: KeyIndex => BuildUtil[_], projects: Map[URI, LoadedBuildUnit]): StructureIndex =
  {
    val keys = Index.allKeys(settings)
    val attributeKeys = Index.attributeKeys(data) ++ keys.map(_.key)
    val scopedKeys = keys ++ data.allKeys((s, k) => ScopedKey(s, k)).toVector
    val projectsMap = projects.mapValues(_.defined.keySet)
    val configsMap: Map[String, Seq[Configuration]] =
      projects.values.flatMap(bu => bu.defined map { case (k, v) => (k, v.configurations) }).toMap
    val keyIndex = keyIndexApply(scopedKeys.toVector, projectsMap, configsMap)
    val aggIndex = keyIndexAggregate(scopedKeys.toVector, extra(keyIndex), projectsMap, configsMap)
    new StructureIndex(Index.stringToKeyMap(attributeKeys), Index.taskToKeyMap(data), Index.triggers(data), keyIndex, aggIndex)
  }

  def setDefinitionKey[T](tk: Task[T], key: ScopedKey[_]): Task[T] =
    if (isDummy(tk)) tk else Task(tk.info.set(Keys.taskDefinitionKey, key), tk.work)

  private def isDummy(t: Task[_]): Boolean = t.info.attributes.get(isDummyTask) getOrElse false
  private val Invisible = Int.MaxValue
  private val isDummyTask = AttributeKey[Boolean]("is-dummy-task", "Internal: used to identify dummy tasks.  sbt injects values for these tasks at the start of task execution.", Invisible)
}
