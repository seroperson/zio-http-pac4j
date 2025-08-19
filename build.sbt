ThisBuild / scalaVersion := "2.13.16"
ThisBuild / organization := "me.seroperson"
ThisBuild / version := "0.1.0"

// Scala versions for cross-compilation
ThisBuild / crossScalaVersions := Seq("2.13.16", "3.3.6")

// Dependency versions
val zioVersion = "2.1.20"
val zioHttpVersion = "3.4.0"
val pac4jVersion = "6.2.0"
val scalaTestVersion = "3.2.19"

ThisBuild / assemblyMergeStrategy := { (_: String) =>
  MergeStrategy.first
}

// Common settings
lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-language:_",
    "-encoding",
    "UTF-8",
    "-feature"
  )
)

// Core dependencies
lazy val coreDependencies = Seq(
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-http" % zioHttpVersion,
  "org.pac4j" % "pac4j-core" % pac4jVersion
)

// Test dependencies
lazy val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test
)

// Example dependencies
lazy val exampleDependencies = Seq(
  "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.5.0",
  "org.pac4j" % "pac4j-oauth" % pac4jVersion,
  "org.pac4j" % "pac4j-jwt" % pac4jVersion,
  "org.pac4j" % "pac4j-saml" % pac4jVersion,
  "org.pac4j" % "pac4j-http" % pac4jVersion
)

// Main library module
lazy val `zio-http-pac4j` = (project in file("zio-http-pac4j"))
  .settings(commonSettings)
  .settings(
    name := "zio-http-pac4j",
    libraryDependencies ++= coreDependencies ++ testDependencies,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),

    // Publishing settings
    publishMavenStyle := true,
    licenses := List("MIT" -> url("https://opensource.org/licenses/MIT")),
    description := "Security library for zio-http based on pac4j",
    homepage := Some(url("https://github.com/seroperson/zio-http-pac4j")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/seroperson/zio-http-pac4j"),
        "scm:git@github.com:seroperson/zio-http-pac4j.git"
      )
    ),
    developers := List(
      Developer(
        id = "seroperson",
        name = "Daniil Sivak",
        email = "seroperson@gmail.com",
        url = url("https://seroperson.me/")
      )
    ),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  )

lazy val `zio-minimal` = (project in file("example/zio-minimal"))
  .dependsOn(`zio-http-pac4j`)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= coreDependencies ++ exampleDependencies,
    assembly / assemblyJarName := "out.jar",
    assemblyJarName := "out.jar",
    Compile / mainClass := Some("ZioApi"),
    publish / skip := true
  )

lazy val `zio-sveltekit-backend` =
  (project in file("example/zio-sveltekit/backend"))
    .dependsOn(`zio-http-pac4j`)
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= coreDependencies ++ exampleDependencies,
      assembly / assemblyJarName := "out.jar",
      Compile / mainClass := Some("ZioApi"),
      publish / skip := true
    )

// Root project
lazy val root = (project in file("."))
  .aggregate(`zio-http-pac4j`, `zio-minimal`, `zio-sveltekit-backend`)
  .settings(
    publish / skip := true
  )
