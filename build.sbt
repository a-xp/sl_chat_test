name := "perf_chat"

version := "1.0"

scalaVersion := "2.11.8"

val akkaHttpVer = "10.0.0"
val akkaVer = "2.4.14"

libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-http_2.11" % akkaHttpVer,
  "org.json4s" %% "json4s-native" % "3.5.0",
  "org.scaldi" %% "scaldi-akka" % "0.5.8",
  "com.typesafe.akka" %% "akka-actor" % akkaVer
)