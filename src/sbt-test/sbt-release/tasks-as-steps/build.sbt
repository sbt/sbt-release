organization := "com.example"
version := "1.2.3"

lazy val myTask = taskKey[Unit]("My task")
lazy val myAggregatedTask = taskKey[Unit]("My aggregated task")
lazy val myInputTask = inputKey[Unit]("My input task")
lazy val testOutputDir = settingKey[File]("")

lazy val root: Project = (project in file("."))
  .aggregate(sub)
  .settings(
    myAggregatedTaskSetting,
    testOutputDir := file("root-out"),
    myTask := {
      IO.write(testOutputDir.value / "mytask", "ran")
    },
    myInputTask := {
      val file = Def.spaceDelimited().parsed.headOption.getOrElse("myinputtask")
      IO.write(testOutputDir.value / file, "ran")
    },
    commands ++= Seq(myCommand, myInputCommand, myCommand2, myInputCommand2),
    releaseProcess := Seq[ReleaseStep](
      releaseStepTask(myTask),
      releaseStepTaskAggregated(root / myAggregatedTask),
      releaseStepInputTask(myInputTask),
      releaseStepInputTask(myInputTask, " custominputtask"),
      releaseStepCommand(myCommand),
      releaseStepCommand(myInputCommand),
      releaseStepCommand(myInputCommand, " custominputcommand"),
      releaseStepCommand("mycommand2"),
      releaseStepCommand("myinputcommand2"),
      releaseStepCommand("myinputcommand2 custominputcommand2")
    ),
  )

lazy val sub = (project in file("sub"))
  .settings(
    myAggregatedTaskSetting,
    testOutputDir := file("sub-out"),
  )

def myAggregatedTaskSetting = myAggregatedTask := {
  IO.write(testOutputDir.value / "myaggregatedtask", "ran")
}

lazy val myCommand = Command.command("mycommand") { state =>
  IO.write(Project.extract(state).get(testOutputDir) / "mycommand", "ran")
  state
}
lazy val myInputCommand = Command.make("myinputcommand") { state =>
  Def.spaceDelimited().map { args => () =>
    val file = args.headOption.getOrElse("myinputcommand")
    IO.write(Project.extract(state).get(testOutputDir) / file, "ran")
    state
  }
}
lazy val myCommand2 = Command.command("mycommand2") { state =>
  IO.write(Project.extract(state).get(testOutputDir) / "mycommand2", "ran")
  state
}
lazy val myInputCommand2 = Command.make("myinputcommand2") { state =>
  Def.spaceDelimited().map { args => () =>
    val file = args.headOption.getOrElse("myinputcommand2")
    IO.write(Project.extract(state).get(testOutputDir) / file, "ran")
    state
  }
}
