ThisBuild / scalaVersion := "2.13.16"
ThisBuild / organization := "me.seroperson"

// Dependency versions
val zioVersion = "2.1.21"
val zioHttpVersion = "3.5.1"
val pac4jVersion = "6.2.2"
val scalaTestVersion = "3.2.19"

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

lazy val exampleSettings = Seq(
  // Kill the application if requested
  Global / cancelable := true,
  // Allows to run the application not in the sbt's jvm itself
  run / fork := true,
  // GraalJS tests sometimes fail without forking
  Test / fork := true,
  // Forward stdin from the sbt shell to the application
  connectInput / run := true,
  // Don't publish examples
  publish / skip := true,
  // Fixed version to hardcode it in docker-compose build
  version := "0.1.0",
  // Resolving opensaml dependencies
  resolvers += "JBoss Repository" at "https://repository.jboss.org/nexus/content/repositories/public/",
  libraryDependencies ++= coreDependencies ++ exampleDependencies
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
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  "org.pac4j" % "pac4j-http" % pac4jVersion % Test
)

// Example dependencies
lazy val exampleDependencies = Seq(
  "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.5.1",
  "org.pac4j" % "pac4j-oauth" % pac4jVersion,
  "org.pac4j" % "pac4j-jwt" % pac4jVersion,
  "org.pac4j" % "pac4j-saml" % pac4jVersion,
  "org.pac4j" % "pac4j-http" % pac4jVersion
)

// Main library module
lazy val `zio-http-pac4j` = (project in file("zio-http-pac4j"))
  .settings(commonSettings)
  .settings(
    crossScalaVersions := Seq("2.13.16", "3.3.6")
  )
  .settings(
    name := "zio-http-pac4j",
    libraryDependencies ++= coreDependencies ++ testDependencies,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),

    // Publishing settings
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
    )
  )

lazy val `zio-form-oauth` =
  (project in file("example/zio-form-oauth"))
    .dependsOn(`zio-http-pac4j`)
    .enablePlugins(JavaAppPackaging)
    .settings(commonSettings)
    .settings(exampleSettings)

lazy val `zio-jwt` =
  (project in file("example/zio-jwt"))
    .dependsOn(`zio-http-pac4j`)
    .enablePlugins(JavaAppPackaging)
    .settings(commonSettings)
    .settings(exampleSettings)

lazy val `zio-sveltekit-backend` =
  (project in file("example/zio-sveltekit/backend"))
    .dependsOn(`zio-http-pac4j`)
    .enablePlugins(JavaAppPackaging)
    .settings(commonSettings)
    .settings(exampleSettings)

// Root project
lazy val root = (project in file("."))
  .aggregate(
    `zio-http-pac4j`,
    `zio-form-oauth`,
    `zio-jwt`,
    `zio-sveltekit-backend`
  )
  .settings(
    publish / skip := true
  )
