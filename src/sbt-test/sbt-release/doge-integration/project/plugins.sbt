libraryDependencies ++= {
  // sbt-doge has been included in sbt 1.0 so this plugin is only available for 0.13
  if(sbtVersion.value.startsWith("0.13"))
    Seq(Defaults.sbtPluginExtra("com.eed3si9n" % "sbt-doge" % "0.1.5", sbtBinaryVersion.value, scalaBinaryVersion.value))
  else
    Nil
}

{
  val pluginVersion = System.getProperty("plugin.version")
  if(pluginVersion == null)
    throw new RuntimeException("""|The system property 'plugin.version' is not defined.
                                  |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  else addSbtPlugin("com.github.gseitz" % "sbt-release" % pluginVersion)
}
