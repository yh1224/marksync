// It uses to import dependencies to IntelliJ.
lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      scalaVersion := "2.13.1"
    )),
    libraryDependencies ++= Seq(
      "com.lihaoyi" % "ammonite" % "1.7.4" cross CrossVersion.full,
      "com.fasterxml.jackson.core" % "jackson-core" % "2.10.0.pr3",
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.9.9",
      "com.fasterxml.jackson.module" % "jackson-module-scala_2.12" % "2.10.0.pr3",
      "io.github.cdimascio" % "java-dotenv" % "5.1.2",
      "net.sourceforge.plantuml" % "plantuml" % "6703",
      "org.slf4j" % "slf4j-log4j12" % "1.7.28",
      "software.amazon.awssdk" % "bom" % "2.9.7",
      "software.amazon.awssdk" % "apache-client" % "2.9.7",
      "software.amazon.awssdk" % "aws-sdk-java" % "2.9.7"
    )
  )
