organization := "com.lightbend.akka.samples"
name := "akka-sample-persistence-scala"

scalaVersion := "2.13.10"
val AkkaVersion = "2.8.5"
val AkkaDiagnosticsVersion = "2.0.0"
val LogbackClassicVersion = "1.2.11"
val ScalaTestVersion = "3.1.1"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.lightbend.akka" %% "akka-diagnostics" % AkkaDiagnosticsVersion,
  "ch.qos.logback" % "logback-classic" % LogbackClassicVersion,
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  "com.lightbend.akka" %% "akka-persistence-jdbc" % "5.2.1",
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
)
//TODO: change to not deprecated version
/*scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint")

// show full stack traces and test case durations
testOptions in Test += Tests.Argument("-oDF")
logBuffered in Test := false*/

run / fork := true