# SBT-RELEASE
This sbt plugin provides a customizable release process that you can add to your project.

## Requirements
 * sbt 0.11.0 or greater
 * The version of the project should follow the semantic versioning scheme on [semver.org](http://www.semver.org) with the following additions:
   * The minor and bugfix part of the version are optional.
   * The appendix after the bugfix part must be alphanumeric (`[0-9a-zA-Z]`) but may also contain dash characters `-`.
   * These are all valid version numbers:
     * 1.2.3
     * 1.2.3-SNAPSHOT
     * 1.2beta1
     * 1.2
     * 1
     * 1-BETA17
 * git [optional]

## Usage
### Adding the plugin dependency

    resolvers += "gseitz@github" at "http://gseitz.github.com/maven/"

    addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.1")

### Including sbt-release settings
**Important:** The settings `releaseSettings` only need to be included in the **root project's** setting.
Make sure they are not included in a settings value that is used for all sub-projects as well. 

#### build.sbt (simple build definition)

    import sbtrelease.Release._

    seq(releaseSettings: _*)

#### build.scala (full build definition)

    import sbtrelease.Release._

    object MyBuild extends Build {
      lazy val MyProject(
        id = "myproject",
        base = file("."),
        settings = Defaults.defaultSettings ++ releaseSettings ++ Seq( /* custom settings here */ )
      )
    }

## `version.sbt`
Since the build definition is actual Scala code, it's not as straight forward to change something in the middle of
it as it is with an XML definition.

For this reason, *sbt-release* won't ever touch your build definition files,
but instead writes the new release or development version to a file called **`version.sbt`** in the root directory of the project.


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
 1. Tag the previous commit with `v$version` (eg. `v1.2`, `v1.2.3`).
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

## Not all releases are created equal
The release process can be customized to the project's needs.

Not using Git? Then strip it out.

Want to check for the existance of release notes at the start and then publish it with [posterous-sbt](https://github.com/n8han/posterous-sbt) at the end? Just add it.


The release process is defined by state transformation functions (`State => State`) and stored in the setting `releaseProcess`.
Take a look at the [default definition](https://github.com/gseitz/sbt-release/blob/master/src/main/scala/ReleasePlugin.scala#L49) before continuing.

If you project's release process differs from the one outlined above, you can provide a different one yourself.

Here is one, that doesn't use git:

    import sbtrelease._

    // ...

    releaseProcess <<= thisProjectRef apply { ref =>
      import ReleaseStateTransformations._
      Seq[ReleasePart](
        checkSnapshotDependencies,
        inquireVersions,
        runTest,
        setReleaseVersion,
        runTest,
        releaseTask(publish in Global in ref),
        setNextVersion,
      )
    }

Notice that the overall process was the same, only the git specific tasks were left out.

Now let's add steps for [posterous-sbt](https://github.com/n8han/posterous-sbt):

    import posterous.Publish._
    import sbtrelease._

    // ...

    releaseProcess <<= thisProjectRef apply { ref =>
      import ReleaseStateTransformations._
      Seq[ReleasePart](
        checkSnapshotDependencies,
        releaseTask(check in Posterous in ref), // upfront check
        inquireVersions,
        runTest,
        setReleaseVersion,
        runTest,
        releaseTask(publish in Global in ref),
        releaseTask(publish in Posterous in ref), // publish release notes
        setNextVersion,
      )
    }

We added the check at the start, to make sure we have everything set up to post the release notes later on.

After publishing the actual build artifacts, we also publish the release notes.

**Side note:** Since the release process consists of state transformation functions (`State => State`),
we can't just add tasks directly. But we can use the helper function `releaseTask` that wraps a state transformation
function around the task and evaluates the task when needed.

## Credits
Thank you Jason (@retronym) and Mark (@harrah) for your feedback and ideas.
