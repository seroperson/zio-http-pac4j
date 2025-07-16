//| mvnDeps: ["com.lihaoyi::mill-contrib-docker:1.0.3", "com.github.vic::mill-dotenv:5dbd874"]
//| mill-version: 1.0.3
package build

import mill._
import mill.scalalib._
import mill.scalalib.publish._
import mill.contrib.docker._
import mill.dotenv.DotEnvModule

object Version {
  val scala213 = "2.13.16"
  val scala336 = "3.3.6"
  val scalaCross = Seq(scala213, scala336)

  val zio = "2.1.20"
  val zioHttp = "3.4.0"
  val pac4j = "6.2.0"
  val scalaTest = "3.2.19"
}

object Library {
  // Core dependencies
  val zio = mvn"dev.zio::zio::${Version.zio}"
  val zioHttp = mvn"dev.zio::zio-http::${Version.zioHttp}"
  val pac4jCore = mvn"org.pac4j:pac4j-core:${Version.pac4j}"

  // Testing
  val scalaTest = mvn"org.scalatest::scalatest:${Version.scalaTest}"
  val zioTest = mvn"dev.zio::zio-test::${Version.zio}"
  val zioTestSbt = mvn"dev.zio::zio-test-sbt::${Version.zio}"

  // Examples
  val zioLoggingSlf4jBridge = mvn"dev.zio::zio-logging-slf4j2-bridge::2.5.0"
  val pac4jOAuth = mvn"org.pac4j:pac4j-oauth:${Version.pac4j}"
  val pac4jJwt = mvn"org.pac4j:pac4j-jwt:${Version.pac4j}"
  val pac4jSaml = mvn"org.pac4j:pac4j-saml:${Version.pac4j}"
  val pac4jHttp = mvn"org.pac4j:pac4j-http:${Version.pac4j}"
}

trait BaseModule extends CrossScalaModule {

  override def repositoriesTask = Task.Anon {
    super.repositoriesTask() ++ Seq(
      coursier.maven.MavenRepository.apply(
        // pac4j itself
        "https://repository.jboss.org/nexus/content/repositories/public/"
      )
    )
  }

  override def scalacOptions = Seq(
    "-unchecked",
    "-deprecation",
    "-language:_",
    "-encoding",
    "UTF-8",
    "-feature"
  )

}

object `zio-http-pac4j` extends Cross[ZioHttpPac4jModule](Version.scalaCross)
trait ZioHttpPac4jModule extends BaseModule with PublishModule {

  override def publishVersion: T[String] =
    "0.1.0"

  override def pomSettings = PomSettings(
    description = "Security library for zio-http based on pac4j",
    organization = "me.seroperson",
    url = "https://github.com/seroperson/zio-http-pac4j",
    licenses = Seq(License.Common.MIT),
    versionControl = VersionControl.github("seroperson", "zio-http-pac4j"),
    developers = Seq(
      Developer("seroperson", "Daniil Sivak", "https://seroperson.me/")
    )
  )

  override def mvnDeps = super.mvnDeps() ++ Seq(
    Library.zio,
    Library.zioHttp,
    Library.pac4jCore
  )

  object test extends ScalaTests with TestModule.ScalaTest {

    override def mvnDeps = super.mvnDeps() ++ Seq(
      Library.scalaTest,
      Library.zioTest,
      Library.zioTestSbt
    )

    override def testFramework = "zio.test.sbt.ZTestFramework"
  }
}

object example extends Module {

  object `zio-minimal` extends Cross[ZioModule](Version.scalaCross)

  object `zio-sveltekit` extends Module {
    object backend extends Cross[ZioModule](Version.scalaCross)
  }

  trait ZioModule extends BaseModule with DockerModule with DotEnvModule {

    object docker extends DockerConfig {
      def baseImage = "openjdk:21-jdk-slim"
      def exposedPorts = Seq(9000)
    }

    override def mvnDeps = super.mvnDeps() ++ Seq(
      Library.zio,
      Library.zioHttp,
      Library.zioLoggingSlf4jBridge,
      Library.pac4jOAuth,
      Library.pac4jJwt,
      Library.pac4jSaml,
      Library.pac4jHttp
    )

    override def moduleDeps =
      super.moduleDeps ++ Seq(`zio-http-pac4j`())

    override def mainClass = Some("ZioApi")
  }

}
