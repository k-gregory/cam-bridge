ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.1"

ThisBuild / semanticdbEnabled := true

val http4sVersion = "0.23.24"
val circeVersion = "0.14.1"

lazy val root = (project in file("."))
  .settings(
    name := "webrtc-webcam",

    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
    ),

    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-core" % http4sVersion,
      "org.http4s" %% "http4s-client" % http4sVersion,
      "org.http4s" %% "http4s-server" % http4sVersion,
    ),

    libraryDependencies += "org.slf4j" % "slf4j-api" % "2.0.12",
    libraryDependencies += "org.slf4j" % "slf4j-reload4j" % "2.0.12",
    libraryDependencies += "org.typelevel" %% "log4cats-slf4j"   % "2.6.0",

    libraryDependencies += "org.freedesktop.gstreamer" % "gst1-java-core" % "1.4.0",

    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion)
  )


Compile / run / fork := true