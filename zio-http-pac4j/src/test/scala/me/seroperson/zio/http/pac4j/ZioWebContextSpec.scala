package me.seroperson.zio.http.pac4j

import zio.test._
import zio.test.Assertion._
import org.pac4j.core.context.Cookie
import zio.ZLayer
import zio.test.ZIOSpecDefault
import me.seroperson.zio.http.pac4j.config.SecurityConfig
import me.seroperson.zio.http.pac4j.config.SessionCookieConfig
import me.seroperson.zio.http.pac4j.config.CallbackConfig
import me.seroperson.zio.http.pac4j.config.LogoutConfig
import me.seroperson.zio.http.pac4j.session.InMemorySessionRepository
import me.seroperson.zio.http.pac4j.ZioPac4jDefaults
import me.seroperson.zio.http.pac4j.session.SessionRepository
import org.pac4j.core.context.session.SessionStore
import zio._
import me.seroperson.zio.http.pac4j.adapter.ZioWebContext
import zio.http.Request
import org.pac4j.core.util.Pac4jConstants
import zio.http.Header.SetCookie
import zio.http.Header
import zio.http.Cookie.SameSite
import zio.http.Path
import me.seroperson.zio.http.pac4j.config.LoginConfig

object ZioWebContextSpec extends ZIOSpecDefault {

  private val defaultLayers = ZLayer.succeed {
    SecurityConfig(clients = List())
  } >+> InMemorySessionRepository.live >+> ZioPac4jDefaults.live

  def spec = suite("ZioWebContext")(
    test("forward the cookie attributes") {
      val request = Request.get("/id")
      val webContext = new ZioWebContext(Request())
      val cookie = new Cookie("cookie_name", "cookie_value")
      cookie.setMaxAge(1234)
      cookie.setDomain("www.example.com")
      cookie.setPath("/example/path")
      cookie.setSecure(true)
      cookie.setHttpOnly(true)
      cookie.setSameSitePolicy("Lax")
      webContext.addResponseCookie(cookie)
      val responseCookie = webContext.getResponse.headers
        .get(Header.SetCookie)
        .collect {
          case x @ SetCookie(
                zio.http.Cookie.Response(
                  name,
                  content,
                  _,
                  _,
                  _,
                  _,
                  _,
                  _
                )
              ) if name == cookie.getName =>
            x.value
        }

      val maxAge =
        Option(cookie.getMaxAge).filter(_ != -1).map(_.toLong.seconds)
      val sameSite =
        Option(cookie.getSameSitePolicy()).map(_.toLowerCase()).map {
          case "strict" => SameSite.Strict
          case "lax"    => SameSite.Lax
          case _        => SameSite.None
        }

      val expectedCookie = zio.http.Cookie.Response(
        name = cookie.getName,
        content = cookie.getValue,
        domain = Option(cookie.getDomain),
        path = Some(Path(cookie.getPath)),
        isSecure = cookie.isSecure,
        isHttpOnly = cookie.isHttpOnly,
        maxAge = maxAge,
        sameSite = sameSite
      )

      assert(responseCookie)(equalTo(Some(expectedCookie)))
    }
  )
}
