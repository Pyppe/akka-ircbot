import sbt._
import Keys._
import com.github.retronym.SbtOneJar

object IrcBotBuild extends Build {

  import Dependencies._

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "fi.pyppe.ircbot",
    version      := "0.1-SNAPSHOT",
    scalaVersion := "2.10.2",
    exportJars   := true
    //offline := true
  )

  lazy val root = Project(
    id = "akka-ircbot",
    base = file("."),
    settings = buildSettings,
    aggregate = Seq(common, master, slave)
  )

  lazy val common = Project(
    id = "common",
    base = file("common"),
    settings = buildSettings ++ Seq(
      libraryDependencies ++= commonLibs
    )
  )

  lazy val master = Project(
    id = "master",
    base = file("master"),
    settings =
      buildSettings ++ Seq(libraryDependencies ++= commonLibs ++ masterLibs) ++ SbtOneJar.oneJarSettings
  ).dependsOn(common)

  lazy val slave = Project(
    id = "slave",
    base = file("slave"),
    settings =
      buildSettings ++ Seq(libraryDependencies ++= commonLibs ++ slaveLibs) ++ SbtOneJar.oneJarSettings
  ).dependsOn(common)

}

object Dependencies {

  private val scalaLangExclusions = ExclusionRule(organization = "org.scala-lang")

  val commonLibs = Seq(
    "com.typesafe.akka"  %% "akka-actor"           % "2.2.1",
    "com.typesafe.akka"  %% "akka-remote"          % "2.2.1",
    "com.typesafe.akka"  %% "akka-slf4j"           % "2.2.1",

    "com.typesafe"       %% "scalalogging-slf4j"   % "1.0.1" excludeAll(scalaLangExclusions),
    "com.typesafe"       %  "config"               % "1.0.2",
    "org.joda"           %  "joda-convert"         % "1.5",
    "joda-time"          %  "joda-time"            % "2.3",
    "ch.qos.logback"     %  "logback-classic"      % "1.0.13",

    "org.specs2"         %% "specs2"               % "2.3.3" % "test"
  )

  val masterLibs = Seq(
    "org.pircbotx"       %  "pircbotx"             % "2.0"
  )

  val slaveLibs = Seq(
    "org.jsoup"               %  "jsoup"                 % "1.7.2",
    "org.json4s"              %% "json4s-jackson"        % "3.2.7",
    "org.json4s"              %% "json4s-ext"            % "3.2.7",
    "org.twitter4j"           %  "twitter4j-core"        % "3.0.5",
    "net.databinder.dispatch" %% "dispatch-core"         % "0.11.0",
    "commons-io"              %  "commons-io"            % "2.4",

    "org.elasticsearch"       %  "elasticsearch"         % "1.0.1"
  )


}