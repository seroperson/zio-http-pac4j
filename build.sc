import mill._
import mill.scalalib._
import mill.scalalib.publish._

object Version {
  val scala212 = "2.12.20"
  val scala213 = "2.13.16"
  val scala336 = "3.3.6"
  val scalaCross = Seq(scala213 /*, scala336*/ )

  val zio = "2.1.19"
  val zioHttp = "3.3.3"
  val pprint = "0.9.0"
  val pac4j = "6.2.0"
  val scalaTest = "3.2.19"
}

object Library {
  // Core dependencies
  val zio = ivy"dev.zio::zio::${Version.zio}"
  val zioHttp = ivy"dev.zio::zio-http::${Version.zioHttp}"
  val zioLogging = ivy"dev.zio::zio-logging-slf4j2-bridge::2.5.0"
  val zioLoggingSlf4j = ivy"dev.zio::zio-logging-slf4j2-bridge::2.5.0"

  // pac4j core and common providers
  val pac4jCore = ivy"org.pac4j:pac4j-core:${Version.pac4j}"
  val pac4jOAuth = ivy"org.pac4j:pac4j-oauth:${Version.pac4j}"
  val pac4jJwt = ivy"org.pac4j:pac4j-jwt:${Version.pac4j}"
  val pac4jSaml = ivy"org.pac4j:pac4j-saml:${Version.pac4j}"
  val pac4jHttp = ivy"org.pac4j:pac4j-http:${Version.pac4j}"

  // Testing
  val scalaTest = ivy"org.scalatest::scalatest:${Version.scalaTest}"
  val zioTest = ivy"dev.zio::zio-test::${Version.zio}"
  val zioTestSbt = ivy"dev.zio::zio-test-sbt::${Version.zio}"

  // Examples
  val pprint = ivy"com.lihaoyi::pprint:${Version.pprint}"
}

trait BaseModule extends CrossScalaModule {

  override def repositoriesTask = Task.Anon {
    val result = super.repositoriesTask() ++ Seq(
      coursier.maven.MavenRepository.apply(
        "https://repository.jboss.org/nexus/content/repositories/public/"
      )
      /*coursier.ivy.IvyRepository.fromPattern(
        "https://repository.jboss.org/nexus/content/repositories/public/" +:
          coursier.ivy.Pattern.default
      )*/
    )
    result
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

  override def ivyDeps = T {
    super.ivyDeps() ++ Agg(
      Library.zio,
      Library.zioHttp,
      Library.pac4jCore,
      Library.pac4jOAuth,
      Library.pac4jJwt,
      Library.pac4jSaml,
      Library.pac4jHttp
    )
  }

  object test extends ScalaTests with TestModule.ScalaTest {

    override def ivyDeps = T {
      super.ivyDeps() ++ Agg(
        Library.scalaTest,
        Library.zioTest,
        Library.zioTestSbt
      )
    }

    override def testFramework = "zio.test.sbt.ZTestFramework"
  }
}

object example extends Module {

  object `zio-minimal` extends Cross[ZioModule](Version.scalaCross)
  trait ZioModule extends BaseModule {

    override def ivyDeps = T {
      super.ivyDeps() ++ Agg(
        Library.pprint,
        Library.zio,
        Library.zioHttp,
        Library.zioLogging,
        Library.zioLoggingSlf4j
      )
    }

    override def moduleDeps =
      super.moduleDeps ++ Seq(`zio-http-pac4j`())
  }

}
