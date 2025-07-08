import mill._
import mill.scalalib._
import mill.scalalib.publish._

object Version {
  val scala212 = "2.12.20"
  val scala213 = "2.13.16"
  val scala336 = "3.3.6"
  val scalaCross = Seq(scala212, scala213, scala336)

  val zio = "2.1.19"
  val zioHttp = "3.3.3"
  val pprint = "0.9.0"
}

object Library {
  // examples
  val pprint = ivy"com.lihaoyi::pprint:${Version.pprint}"

  val zio = ivy"dev.zio::zio::${Version.zio}"
  val zioHttp = ivy"dev.zio::zio-http::${Version.zioHttp}"
}

trait BaseModule extends CrossScalaModule {

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
    description = "Security library for zio-http based on pac4j using Scala 3",
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
      Library.zio
    )
  }

  object test extends ScalaTests with TestModule.ScalaTest {

    override def ivyDeps = T {
      super.ivyDeps() ++ Agg(
      )
    }
  }
}

object example extends Module {

  object zio extends Cross[ZioModule](Version.scalaCross)
  trait ZioModule extends BaseModule {

    override def ivyDeps = T {
      super.ivyDeps() ++ Agg(
        Library.pprint,
        Library.zio,
        Library.zioHttp
      )
    }

    override def moduleDeps =
      super.moduleDeps ++ Seq(`zio-http-pac4j`())
  }

}
