organization := "com.github.yh1224"

name := "marksync"

version := "0.1.0"

scalaVersion := "2.13.1"

lazy val marksync = (project in file("."))
  .enablePlugins(ConscriptPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-core" % "2.10.1",
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.10.1",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.10.1",
      "com.github.scopt" %% "scopt" % "3.7.1",
      "com.softwaremill.sttp" %% "core" % "1.7.2",
      "com.softwaremill.sttp" %% "okhttp-backend" % "1.7.2",
      "com.lihaoyi" %% "requests" % "0.4.9",
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

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
