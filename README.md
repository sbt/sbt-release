# SBT-RELEASE
This sbt plugin provides a customizable release process that you can add to your project.

**Notice:** This README contains information for the latest snapshots. Please refer to the documents for a specific version by looking up the respective [tag](https://github.com/gseitz/sbt-release/tags).

## Requirements
 * sbt 0.11.1 (since *sbt-release 0.4*)
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
 * A [publish repository](https://github.com/harrah/xsbt/wiki/Publishing) configured. (Required only for the default release process. See further below for release process customizations.)
 * git [optional]

## Usage
### Adding the plugin dependency

Add the following lines to `./project/build.sbt`. See the section [Using Plugins](https://github.com/harrah/xsbt/wiki/Getting-Started-Using-Plugins) in the xsbt wiki for more information.

    resolvers += "gseitz@github" at "http://gseitz.github.com/maven/"

    addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.4")

### Including sbt-release settings
**Important:** The settings `releaseSettings` need to be mixed into every sub-projects `settings`.
This is usually achieved by extracting common settings into a `val standardSettings: Seq[Setting[_]]` which is then included in all sub-projects.

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

## version.sbt
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
 1. Write `version in ThisBuild := "$releaseVersion"` to the file `version.sbt` and also apply this setting to the current [build state](https://github.com/harrah/xsbt/wiki/Build-State).
 1. Commit the changes in `version.sbt`.
 1. Tag the previous commit with `v$version` (eg. `v1.2`, `v1.2.3`).
 1. Run `publish`.
 1. Write `version in ThisBuild := "nextVersion"` to the file `version.sbt` and also apply this setting to the current build state.
 1. Commit the changes in `version.sbt`.

In case of a failure of a task, the release process is aborted.

### Non-interactive release
You can run a non-interactive release by providing the argument `with-defaults` (tab completion works) to the `release` command.
For all interactions, the following default value will be chosen:

 * Continue with snapshots dependencies: no
 * Release Version: current version without the qualifier (eg. `1.2-SNAPSHOT` -> `1.2`)
 * Next Version: increase the minor version segment of the current version and set the qualifier to '-SNAPSHOT' (eg. `1.2.1-SNAPSHOT` -> `1.3.0-SNAPSHOT`)

### Skipping tests
For that emergency release at 2am on a Sunday, you can optionally avoid running any tests by providing the `skip-tests` argument to the `release` command.

### Custom versioning
*sbt-release* comes with two settings for deriving the release version and the next development version from a given version.
These derived versions are used for the suggestions/defaults in the prompt and for non-interactive releases.

Let's take a look at the types:

    val releaseVersion : SettingKey[String => String]
    val nextVersion    : SettingKey[String => String]

The default settings make use of the helper class [`Version`](https://github.com/gseitz/sbt-release/blob/master/src/main/scala/Version.scala) that ships with *sbt-release*.

    // strip the qualifier off the input version, eg. 1.2.1-SNAPSHOT -> 1.2.1
    releaseVersion := { ver => Version(ver).map(_.withoutQualifier.string).getOrElse(versionFormatError) }

    // bump the minor version and append '-SNAPSHOT', eg. 1.2.1 -> 1.3.0-SNAPSHOT
    nextVersion    := { ver => Version(ver).map(_.bumpMinor.asSnapshot.string).getOrElse(versionFormatError) }

If you want to customize the versioning, keep the following in mind:

 * `releaseVersion`
   * input: the current development version
   * output: the release version

 * `nextVersion`
   * input: the release version (either automatically 'chosen' in a non-interactive build or from user input)
   * output: the next development version


## Not all releases are created equal - Customizing the release process
The release process can be customized to the project's needs.

  * Not using Git? Then strip it out.
  * Want to check for the existance of release notes at the start and then publish it with [posterous-sbt](https://github.com/n8han/posterous-sbt) at the end? Just add it.


The release process is defined by [State](http://harrah.github.com/xsbt/latest/api/sbt/State.html) transformation functions (`State => State`), for which *sbt-release* defines the type synonym:

    type ReleasePart = State => State
    
The sequence of `ReleasePart`s that make up the release process is stored in the setting `releaseProcess: SettingKey[Seq[State => State]]`.

The state transformations functions used in *sbt-release* are the same as the action/body part of a no-argument command.
You can read more about [building commands](https://github.com/harrah/xsbt/wiki/Commands) in the sbt wiki.

### Release parts
There are basically 2 ways to creating a new `ReleasePart` (remember, that's just a synonym for `State => State`):

#### Defining your own release parts
You can define your own state tansformation functions, just like *sbt-release* does, for example:

    val checkOrganization: ReleasePart = { st: State =>
      // extract the build state
      val extracted = Project.extract(st)
      // retrieve the value of the organization SettingKey
      val org = extracted.get(Keys.organization)
      
      if (org.startsWith("com.acme")
        sys.error("Hey, no need to release a toy project!")
      
      st
    }
    
We will later see how to make this function a part of the release process.

#### Reusing already defined  tasks
Sometimes you just want to run an already existing task. 
This is especially useful if the task raises an error in case something went wrong and therefore interrupts the release process.

*sbt-release* comes with a [convenience function](https://github.com/gseitz/sbt-release/blob/master/src/main/scala/package.scala)

    releaseTask[T](task: ScopedTask[T]): ReleasePart
    
that takes any scoped task and wraps it in a state transformation function, executing the task when an instance of `State` is applied to the function.


I highly recommend to make yourself familiar with the [State API](http://harrah.github.com/xsbt/latest/api/sbt/State.html) before you continue your journey to a fully customized release process.

### Can we finally customize that release process, please?
Yes, and as a start, let's take a look at the [default definition](https://github.com/gseitz/sbt-release/blob/master/src/main/scala/ReleasePlugin.scala#L49) of `releaseProcess`:

#### The default release process

    import sbtrelease._

    // ...

    releaseProcess <<= thisProjectRef apply { ref =>
      import ReleaseStateTransformations._
      Seq[ReleasePart](
        initialGitChecks,                       // : ReleasePart
        checkSnapshotDependencies,              // : ReleasePart
        inquireVersions,                        // : ReleasePart
        runTest,                                // : ReleasePart
        setReleaseVersion,                      // : ReleasePart
        commitReleaseVersion,                   // : ReleasePart
        tagRelease,                             // : ReleasePart
        releaseTask(publish in Global in ref),  // : TaskKey refurbished as a ReleasePart
        setNextVersion,                         // : ReleasePart
        commitNextVersion                       // : ReleasePart
      )
    }

The names of the individual parts of the release process are pretty much self-describing. 
Notice how we can just reuse the `publish` task by utilizing the `releaseTask` helper function,
but keep in mind that it needs to be properly scoped (more info on [scoping and settings](https://github.com/harrah/xsbt/wiki/Settings)).

#### No Git, and no toy projects!
Let's modify the previous release process and remove the Git parts of it, who uses that anyway.

    import sbtrelease._

    // ...

    releaseProcess <<= thisProjectRef apply { ref =>
      import ReleaseStateTransformations._
      Seq[ReleasePart](
        checkOrganization,                // Look Ma', my own release part!
        checkSnapshotDependencies,
        inquireVersions,
        runTest,
        setReleaseVersion,
        releaseTask(publish in Global in ref),
        setNextVersion,
      )
    }

Overall, the process stayed pretty much the same:
  
  * The Git related parts were left out.
  * Our `checkOrganization` task was added in the beginning, just to be sure this is a serious project.

#### Release notes anyone?
Now let's also add steps for [posterous-sbt](https://github.com/n8han/posterous-sbt):

    import posterous.Publish._
    import sbtrelease._

    // ...

    releaseProcess <<= thisProjectRef apply { ref =>
      import ReleaseStateTransformations._
      Seq[ReleasePart](
        checkOrganization,
        checkSnapshotDependencies,
        releaseTask(check in Posterous in ref),   // upfront check
        inquireVersions,
        runTest,
        setReleaseVersion,
        releaseTask(publish in Global in ref),
        releaseTask(publish in Posterous in ref), // publish release notes
        setNextVersion,
      )
    }

We added the check at the start, to make sure we have everything set up to post the release notes later on.
After publishing the actual build artifacts, we also publish the release notes.

## Credits
Thank you, [Jason](http://github.com/retronym) and [Mark](http://github.com/harrah), for your feedback and ideas.
