{
  // sbt-doge has been included in sbt 1.0 so this plugin is only available for 0.13
  val sbtV = System.getProperty("sbt.version")
  if(sbtV.startsWith("0.13")) addSbtPlugin("com.eed3si9n" % "sbt-doge" % "0.1.5")
  // FIXME: find another noop that satisfies type requirements
  else libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.21"

  val pluginVersion = System.getProperty("plugin.version")
  if(pluginVersion == null)
    throw new RuntimeException("""|The system property 'plugin.version' is not defined.
                                  |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  else addSbtPlugin("com.github.gseitz" % "sbt-release" % pluginVersion)
}
