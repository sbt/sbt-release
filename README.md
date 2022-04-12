# sbt-release
This sbt plugin provides a customizable release process that you can add to your project.

[![sbt-release Scala version support](https://index.scala-lang.org/sbt/sbt-release/sbt-release/latest-by-scala-version.svg?targetType=Sbt)](https://index.scala-lang.org/sbt/sbt-release/sbt-release)

**Notice:** This README contains information for the latest release. Please refer to the documents for a specific version by looking up the respective [tag](https://github.com/sbt/sbt-release/tags).

## Requirements
 * sbt 0.13.5+
 * The version of the project should follow the semantic versioning scheme on [semver.org](https://www.semver.org) with the following additions:
   * The minor and bugfix (and beyond) part of the version are optional.
   * There is no limit to the number of subversions you may have.
   * The appendix after the bugfix part must be alphanumeric (`[0-9a-zA-Z]`) but may also contain dash characters `-`.
   * These are all valid version numbers:
     * 1.2.3
     * 1.2.3-SNAPSHOT
     * 1.2beta1
     * 1.2-beta.1
     * 1.2
     * 1
     * 1-BETA17
     * 1.2.3.4.5
     * 1.2.3.4.5-SNAPSHOT
 * A [publish repository](https://www.scala-sbt.org/1.x/docs/Publishing.html) configured. (Required only for the default release process. See further below for release process customizations.)
 * git [optional]

## Usage

Add the following lines to `./project/plugins.sbt`. See the section [Using Plugins](https://www.scala-sbt.org/1.x/docs/Using-Plugins.html) in the sbt website for more information.

```scala
addSbtPlugin("com.github.sbt" % "sbt-release" % "1.1.0")
```

## version.sbt

Since the build definition is actual Scala code, it's not as straight forward to change something in the middle of it as it is with an XML definition.

For this reason, *sbt-release* won't ever touch your build definition files, but instead writes the new release or development version to a file defined by the setting `releaseVersionFile`, which is set to **`file("version.sbt")`** by default and points to `$PROJECT_ROOT/version.sbt`.

By default the version is set on the build level (using `ThisBuild / version`). This behavior can be controlled by setting `releaseUseGlobalVersion` to `false`, after which a version like `version := "1.2.3"` will be written to `version.sbt`.


## Release Process

The default release process consists of the following tasks:

 1. Check that the working directory is a git repository and the repository has no outstanding changes. Also prints the hash of the last commit to the console.
 1. If there are any snapshot dependencies, ask the user whether to continue or not (default: no).
 1. Ask the user for the `release version` and the `next development version`. Sensible defaults are provided.
 1. Run `clean`.
 1. Run `test:test`, if any test fails, the release process is aborted.
 1. Write `ThisBuild / version := "$releaseVersion"` to the file `version.sbt` and also apply this setting to the current [build state](https://www.scala-sbt.org/1.x/docs/Core-Principles.html#Introduction+to+build+state).
 1. Commit the changes in `version.sbt`.
 1. Tag the previous commit with `v$version` (eg. `v1.2`, `v1.2.3`).
 1. Run `publish`.
 1. Write `ThisBuild / version := "nextVersion"` to the file `version.sbt` and also apply this setting to the current build state.
 1. Commit the changes in `version.sbt`.

In case of a failure of a task, the release process is aborted.

### Non-interactive release

You can run a non-interactive release by providing the argument `with-defaults` (tab completion works) to the `release` command.

For all interactions, the following default value will be chosen:

 * Continue with snapshots dependencies: no
 * Release Version: current version without the qualifier (eg. `1.2-SNAPSHOT` -> `1.2`)
 * Next Version: increase the minor version segment of the current version and set the qualifier to '-SNAPSHOT' (eg. `1.2.1-SNAPSHOT` -> `1.3.0-SNAPSHOT`)
 * VCS tag: default is abort if the tag already exists. It is possible to override the answer to VCS by ```default-tag-exists-answer``` with one of:
    * ```o``` override
    * ```k``` do not overwrite
    * ```a``` abort (default)
    * ```<tag-name>``` an explicit custom tag name (e.g. ```1.2-M3```)
 * VCS push:
    * Abort if no remote tracking branch is set up.
    * Abort if remote tracking branch cannot be checked (eg. via `git fetch`).
    * Abort if the remote tracking branch has unmerged commits.

### Set release version and next version as command arguments

You can set the release version using the argument `release-version` and next version with `next-version`.

Example:

    release release-version 1.0.99 next-version 1.2.0-SNAPSHOT

### Skipping tests

For that emergency release at 2am on a Sunday, you can optionally avoid running any tests by providing the `skip-tests` argument to the `release` command.

### Cross building during a release

Since version 0.7, *sbt-release* comes with built-in support for [cross building](https://www.scala-sbt.org/1.x/docs/Cross-Build.html) and cross publishing. A cross release can be triggered in two ways:

 1. via the setting `releaseCrossBuild` (by default set to `false`)
 1. by using the option `cross` for the `release` command

    `> release cross with-defaults`

Combining both ways of steering a cross release, it is possible to generally disable automatic detection of cross release by using `releaseCrossBuild := false` and running `release cross`.

Of the predefined release steps, the `clean`, `test`, and `publish` release steps are set up for cross building.

A cross release behaves analogous to using the `+` command:
 1. If no `crossScalaVersions` are set, then running `release` or `release cross` will not trigger a cross release (i.e. run the release with the scala version specified in the setting `scalaVersion`).
 1. If the `crossScalaVersions` setting is set, then only these scala versions will be used. Make sure to include the regular/default `scalaVersion` in the `crossScalaVersions` setting as well. Note that setting running `release cross` on a root project with `crossScalaVersions` set to `Nil` will not release anything. 

In the section *Customizing the release process* we take a look at how to define a `ReleaseStep` to participate in a cross build.

### Convenient versioning

As of version 0.8, *sbt-release* comes with four strategies for computing the next snapshot version via the `releaseVersionBump` setting. These strategies are defined in `sbtrelease.Version.Bump`. By default, the `Next` strategy is used:

 * `Major`: always bumps the *major* part of the version
 * `Minor`: always bumps the *minor* part of the version
 * `Bugfix`: always bumps the *bugfix* part of the version
 * `Nano`: always bumps the *nano* part of the version
 * `Next`: bumps the last version part (e.g. `0.17` -> `0.18`, `0.11.7` -> `0.11.8`, `3.22.3.4.91` -> `3.22.3.4.92`)

Example:

    releaseVersionBump := sbtrelease.Version.Bump.Major

### Custom versioning

*sbt-release* comes with two settings for deriving the release version and the next development version from a given version.

These derived versions are used for the suggestions/defaults in the prompt and for non-interactive releases.

Let's take a look at the types:

```scala
val releaseVersion     : SettingKey[String => String]
val releaseNextVersion : SettingKey[String => String]
```

The default settings make use of the helper class [`Version`](https://github.com/sbt/sbt-release/blob/master/src/main/scala/Version.scala) that ships with *sbt-release*.

```scala
// strip the qualifier off the input version, eg. 1.2.1-SNAPSHOT -> 1.2.1
releaseVersion     := { ver => Version(ver).map(_.withoutQualifier.string).getOrElse(versionFormatError(ver)) }

// bump the version and append '-SNAPSHOT', eg. 1.2.1 -> 1.3.0-SNAPSHOT
releaseNextVersion := {
  ver => Version(ver).map(_.bump(releaseVersionBump.value).asSnapshot.string).getOrElse(versionFormatError(ver))
},
```

If you want to customize the versioning, keep the following in mind:

 * `releaseVersion`
   * input: the current development version
   * output: the release version

 * `releaseNextVersion`
   * input: the release version (either automatically 'chosen' in a non-interactive build or from user input)
   * output: the next development version

### Custom VCS messages
*sbt-release* has built in support to commit/push to Git, Mercurial and Subversion repositories. The messages for the tag and the commits can be customized to your needs with these settings:

```scala
val releaseTagComment        : TaskKey[String]
val releaseCommitMessage     : TaskKey[String]
val releaseNextCommitMessage : TaskKey[String]

// defaults
releaseTagComment        := s"Releasing ${(ThisBuild / version).value}",
releaseCommitMessage     := s"Setting version to ${(ThisBuild / version).value}",
releaseNextCommitMessage := s"Setting version to ${(ThisBuild / version).value}",
```

### Publishing signed releases

SBT is able to publish signed releases using the [sbt-pgp plugin](https://github.com/sbt/sbt-pgp).

After setting that up for your project, you can then tell *sbt-release* to use it by setting the `releasePublishArtifactsAction` key:

```scala
releasePublishArtifactsAction := PgpKeys.publishSigned.value
````

## Customizing the release process

### Not all releases are created equal

The release process can be customized to the project's needs.

  * Not using Git? Then rip it out.
  * Want to check for the existence of release notes at the start of the release and then publish it with [posterous-sbt](https://github.com/n8han/posterous-sbt) at the end? Just add the release step.


The release process is defined by [State](https://www.scala-sbt.org/1.x/docs/Build-State.html) transformation functions (`State => State`), for which *sbt-release* defines this case class:

```scala
case class ReleaseStep (
  action: State => State,
  check: State => State = identity,
  enableCrossBuild: Boolean = false
)
```

The function `action` is used to perform the actual release step. Additionally, each release step can provide a `check` function that is run at the beginning of the release and can be used to prevent the release from running because of an unsatisfied invariant (i.e. the release step for publishing artifacts checks that publishTo is properly set up).  The property `enableCrossBuild` tells *sbt-release* whether or not a particular `ReleaseStep` needs to be executed for the specified `crossScalaVersions`.

The sequence of `ReleaseStep`s that make up the release process is stored in the setting `releaseProcess: SettingKey[Seq[ReleaseStep]]`.

The state transformations functions used in *sbt-release* are the same as the action/body part of a no-argument command.  You can read more about [building commands](https://www.scala-sbt.org/1.x/docs/Commands.html) in the sbt website.

### Release Steps

There are basically 2 ways to creating a new `ReleaseStep`:

#### Defining your own release steps

You can define your own state tansformation functions, just like *sbt-release* does, for example:

```scala
val checkOrganization = ReleaseStep(action = st => {
  // extract the build state
  val extracted = Project.extract(st)
  // retrieve the value of the organization SettingKey
  val org = extracted.get(Keys.organization)

  if (org.startsWith("com.acme"))
    sys.error("Hey, no need to release a toy project!")

  st
})
```

We will later see how to let this release step participate in the release process.

#### Reusing already defined tasks

Sometimes you just want to run an existing task or command. This is especially useful if the task raises an error in case something went wrong and therefore interrupts the release process.

*sbt-release* comes with a few convenience functions for converting tasks and commands to release steps:

* `releaseStepTask` - Run an individual task. Does not aggregate builds.
* `releaseStepTaskAggregated` - Run an aggregated task.
* `releaseStepInputTask` - Run an input task, optionally taking the input to pass to it.
* `releaseStepCommand` - Run a command.

For example:

```scala
releaseProcess := Seq[ReleaseStep](
  releaseStepInputTask(testOnly, " com.example.MyTest"),
  releaseStepInputTask(scripted),
  releaseStepTask(publish in subproject),
  releaseStepCommand("sonatypeRelease")
)
```

I highly recommend to make yourself familiar with the [State API](https://www.scala-sbt.org/1.x/docs/Build-State.html) before you continue your journey to a fully customized release process.

### Can we finally customize that release process, please?

Yes, and as a start, let's take a look at the [default definition](https://github.com/sbt/sbt-release/blob/v1.0.15/src/main/scala/ReleasePlugin.scala#L250) of `releaseProcess`:

#### The default release process

```scala
import ReleaseTransformations._

// ...

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  runClean,                               // : ReleaseStep
  runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  tagRelease,                             // : ReleaseStep
  publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
  setNextVersion,                         // : ReleaseStep
  commitNextVersion,                      // : ReleaseStep
  pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
)
```

The names of the individual steps of the release process are pretty much self-describing.
Notice how we can just reuse the `publish` task by utilizing the `releaseTask` helper function,
but keep in mind that it needs to be properly scoped (more info on [Scopes](https://www.scala-sbt.org/1.x/docs/Scopes.html)).

Note, the `commitReleaseVersion` step requires that the working directory has no untracked files by default. It will abort the release in this case. You may disable this check
by setting the `releaseIgnoreUntrackedFiles` key to `true`.

#### No Git, and no toy projects!

Let's modify the previous release process and remove the Git related steps, who uses that anyway.

```scala
import ReleaseTransformations._

// ...

ReleaseKeys.releaseProcess := Seq[ReleaseStep](
  checkOrganization,                // Look Ma', my own release step!
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  publishArtifacts,
  setNextVersion
)
```

Overall, the process stayed pretty much the same:

  * The Git related steps were left out.
  * Our `checkOrganization` task was added in the beginning, just to be sure this is a serious project.

#### Release notes anyone?
Now let's also add steps for [posterous-sbt](https://github.com/n8han/posterous-sbt):

```scala
import posterous.Publish._
import ReleaseTransformations._

// ...

val publishReleaseNotes = (ref: ProjectRef) => ReleaseStep(
  check  = releaseStepTaskAggregated(check in Posterous in ref),   // upfront check
  action = releaseStepTaskAggregated(publish in Posterous in ref) // publish release notes
)

// ...

ReleaseKeys.releaseProcess <<= thisProjectRef apply { ref =>
  import ReleaseStateTransformations._
  Seq[ReleaseStep](
    checkOrganization,
    checkSnapshotDependencies,
    inquireVersions,
    runTest,
    setReleaseVersion,
    publishArtifacts,
    publishReleaseNotes(ref) // we need to forward `thisProjectRef` for proper scoping of the underlying tasks
    setNextVersion
  )
}
```

The `check` part of the release step is run at the start, to make sure we have everything set up to post the release notes later on.
After publishing the actual build artifacts, we also publish the release notes.

## Credits
Thank you, [Jason](https://github.com/retronym) and [Mark](https://github.com/harrah), for your feedback and ideas.

## Contributors
[Johannes Rudolph](https://github.com/jrudolph), [Espen Wiborg](https://github.com/espenhw), [Eric Bowman](https://github.com/ebowman), [Petteri Valkonen](https://github.com/pvalkone),
[Gary Coady](https://github.com/garycoady), [Alexey Alekhin](https://github.com/laughedelic), [Andrew Gustafson](https://github.com/agustafson), [Paul Davies](https://github.com/paulmdavies),
[Stanislav Savulchik](https://github.com/savulchik), [Tim Van Laer](https://github.com/timvlaer), [Lars Hupel](https://github.com/larsrh)

## License
Copyright (c) 2011-2014 Gerolf Seitz

Published under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt)
