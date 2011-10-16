# SBT-RELEASE
This sbt plugin provides a customizable release process that you can add to your project.

## Requirements
 * sbt 0.11.0 or greater
 * The version of the project adheres to the pattern `[0-9]+(.[0-9)+)?(.[0-9]+)?(-.*)?`
 * git [optional]

## Usage
### Adding the plugin dependency

    resolvers += "gseitz@github" at "http://gseitz.github.com/maven/"

    addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.1")

### Import sbt-release settings
##### build.sbt (simple build definition)

    import sbtrelease.Release._

    seq(releaseSettings: _*)

##### build.scala (full build definition)

    import sbtrelease.Release._

    object MyBuild extends Build {
      lazy val MyProject(
        id = "myproject",
        base = file("."),
        settings = Defaults.defaultSettings ++ releaseSettings ++ Seq( /* custom settings here */ )
      )
    }

## Release Process
The default release process consists of the following tasks:

 1. Check that the working directory is a git repository and the repository has no outstanding changes. Also prints the hash of the last commit to the console.
 1. If there are any snapshot dependencies, ask the user whether to continue or not (default: no).
 1. Ask the user for the `release version` and the `next development version`. Sensible defaults are provided.
 1. Run `test:test`, if any test fails, the release process is aborted.
 1. Write `version in ThisBuild := "$releaseVersion"` to the file `version.sbt` and also apply this setting to the current build state.
 1. Run `test:test`.
 1. Run `publish`.
 1. Commit the changes in `version.sbt`.
 1. Tag the previous commit with 'Releasing $version`.
 1. Write `version in ThisBuild := "nextVersion"` to the file `version.sbt` and also apply this setting to the current build state.

In case of a failure of a task, the release process is aborted.

### Non-interactive release
You can run a non-interactive release by prividing the argument `with-defaults` (tab completion works) to the `release` command.
For all interactions, the following default value will be chosen:

 * Continue with snapshots dependencies: no
 * Release Version: current version without the qualifier (eg. 1.2-SNAPSHOT -> 1.2)
 * Next Version: increase the last segment of the current version and set the qualifier to '-SNAPSHOT' (eg. 1.2-SNAPSHOT -> 1.3-SNAPSHOT)

### Skipping tests
For that emergency release at 2am on a Sunday, you can optionally avoid running any tests by providing the `skip-tests` argument to the `release` command.

