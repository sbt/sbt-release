import sbtrelease.ReleaseStateTransformations._
import sbtrelease.Compat._

val Scala210 = "2.10.6"

val SupportedScalaVersions = Seq(Scala210, "2.11.8")

val commonSettings = Seq(
  scalaVersion := Scala210,
  publishTo := Some(Resolver.file("file", new File("artifacts"))),
  releaseCrossBuild := false,
  releaseIgnoreUntrackedFiles := true,
  releaseProcess := Seq[ReleaseStep](
    releaseStepCommandAndRemaining("+publish")
  )
)

lazy val root = {
  val p = (project in file("."))
    .settings(commonSettings: _*)
    .settings(publishArtifact := false)
    .aggregate(library, plugin)

  val doge = try {
    val field = Class.forName("sbtdoge.CrossPerProjectPlugin$").getField(scala.reflect.NameTransformer.MODULE_INSTANCE_NAME)
    Some(field.get(null).asInstanceOf[AutoPlugin])
  } catch {
    case _: ClassNotFoundException =>
      None
  }

  doge.fold(p)(p.enablePlugins(_))
}

// since it's a library it should be cross published
val library = (project in file("library"))
  .settings(commonSettings: _*)
  .settings(crossScalaVersions := SupportedScalaVersions)

// since it's an sbt plugin, it should only be published for 2.10
val plugin = (project in file("plugin"))
  .settings(commonSettings: _*)
  .settings(sbtPlugin := true, sbtVersion in pluginCrossBuild := "0.13.17", crossScalaVersions := Seq(Scala210))
  .dependsOn(library)
