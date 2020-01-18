// It uses to import dependencies to IntelliJ.
lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      scalaVersion := "2.13.1"
    )),
    libraryDependencies ++= Seq(
      "com.lihaoyi" % "ammonite" % "2.0.4" cross CrossVersion.full,
      "com.fasterxml.jackson.core" % "jackson-core" % "2.10.1",
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.10.1",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.10.1",
      "com.softwaremill.sttp" %% "core" % "1.7.2",
      "com.softwaremill.sttp" %% "okhttp-backend" % "1.7.2",
      "commons-codec" % "commons-codec" % "1.13",
      "io.github.cdimascio" % "java-dotenv" % "5.1.3",
      "io.github.java-diff-utils" % "java-diff-utils" % "4.5",
      "net.sourceforge.plantuml" % "plantuml" % "8059",
      "org.slf4j" % "slf4j-log4j12" % "1.7.30",
      //"software.amazon.awssdk" % "bom" % "2.9.7",
      "software.amazon.awssdk" % "apache-client" % "2.9.7",
      "software.amazon.awssdk" % "aws-sdk-java" % "2.9.7"
    )
  )
