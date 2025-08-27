package me.seroperson.zio.http.pac4j

import zio.test.Assertion._
import zio.test._
import zio.http._
import zio._

import org.pac4j.core.util.Pac4jConstants

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import me.seroperson.zio.http.pac4j.session.SessionRepository
import me.seroperson.zio.http.pac4j.session.ZioSessionStore
import me.seroperson.zio.http.pac4j.adapter.ZioWebContext
import me.seroperson.zio.http.pac4j.session.InMemorySessionRepository
import me.seroperson.zio.http.pac4j.config.SecurityConfig
import me.seroperson.zio.http.pac4j.config.SessionCookieConfig
import me.seroperson.zio.http.pac4j.config.CallbackConfig
import me.seroperson.zio.http.pac4j.config.LogoutConfig
import me.seroperson.zio.http.pac4j.ZioPac4jDefaults
import org.pac4j.core.context.session.SessionStore
import zio.http.Header.SetCookie
import me.seroperson.zio.http.pac4j.Pac4jMiddleware
import org.pac4j.core.profile.UserProfile
import zio.http.Request
import org.pac4j.http.client.indirect.FormClient
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator
import zio.http.Header.HeaderType
import zio.http.Header.Location
import zio.http.MediaTypes
import me.seroperson.zio.http.pac4j.config.LoginConfig

object SessionSpec extends ZIOSpecDefault {

  private val defaultLayers = ZLayer.succeed {
    SecurityConfig(
      clients = List({
        new FormClient(
          /* loginUrl = */ s"/loginForm",
          new SimpleTestUsernamePasswordAuthenticator()
        ).setCallbackUrl(s"/api/callback")
      })
    )
  } >+> InMemorySessionRepository.live >+> ZioPac4jDefaults.live >+> Scope.default

  def spec = suiteAll("Pac4j session management") {

    val allRoutes = (Pac4jMiddleware.callback ++
      Pac4jMiddleware.logout ++
      Pac4jMiddleware.login(clients = List("FormClient")) ++
      Routes(
        Method.GET / Root -> (handler {
          ZIO.succeed(Response.ok)
        } @@ Pac4jMiddleware.errorIfUnauthorized)
      ))

    def loginRequest(client: String, sessionCookies: Iterable[Header.Cookie]) =
      allRoutes(
        Request(
          method = Method.GET,
          url = (url"/api/login": URL)
            .addQueryParam("provider", client)
        )
      )

    suite("Without authorization")(
      test("return 401 on protected route") {
        (for {
          response <- allRoutes(Request(method = Method.GET, url = URL.root))
        } yield assert(response.status)(equalTo(Status.Unauthorized)))
          .provide(defaultLayers)
      },
      test("return 302 to default client on empty callback route") {
        (for {
          response <- allRoutes(
            Request(method = Method.GET, url = url"/api/callback")
          )
        } yield assert(response.status)(equalTo(Status.Found)) &&
          assert(response.header(Location))(
            equalTo(
              Some(Location(url"/loginForm?username=&error=missing_field"))
            )
          ))
          .provide(defaultLayers)
      },
      test("return 302 to default url on logout") {
        (for {
          response <- allRoutes(
            Request(method = Method.GET, url = url"/api/logout")
          )
        } yield assert(response.status)(equalTo(Status.Found)) &&
          assert(response.header(Location))(equalTo(Some(Location(url"/")))))
          .provide(defaultLayers)
      },
      test("return 302 to default client on login") {
        (for {
            unauthorizedLogin <- allRoutes(
              Request(method = Method.GET, url = url"/api/login")
            )

            sessionCookies = TestUtils.collectSessionCookies(unauthorizedLogin)

            protectedResponse <- allRoutes(
              Request(
                method = Method.GET,
                headers = Headers(sessionCookies),
                url = URL.root
              )
            )
          } yield
          // format: off
            assert(unauthorizedLogin.status)(equalTo(Status.Found)) &&
            assert(unauthorizedLogin.header(Location))(equalTo(Some(Location(url"/loginForm")))) &&
            assert(protectedResponse.status)(equalTo(Status.Unauthorized))
          // format: on
        )
          .provide(defaultLayers)
      }
    )

    suite("With authorization")(
      test(
        "return 401 on protected route after retrieving session_id without logging in"
      ) {
        (for {
          login <- loginRequest("FormClient", Iterable.empty)
          request = Request(
            method = Method.GET,
            headers = Headers(TestUtils.collectSessionCookies(login)),
            url = URL.root
          )
          response <- allRoutes(request)
        } yield assert(response.status)(equalTo(Status.Unauthorized)))
          .provide(defaultLayers)
      },
      test("return 200 on protected route after logging in") {
        (for {
          unauthorizedLogin <- loginRequest("FormClient", Iterable.empty)

          sessionCookies = TestUtils.collectSessionCookies(unauthorizedLogin)

          callbackResponse <- allRoutes(
            Request(
              method = Method.POST,
              headers = Headers(
                sessionCookies ++ Seq(
                  Header.ContentType(
                    MediaType.application.`x-www-form-urlencoded`
                  )
                )
              ),
              url = (url"/api/callback": URL)
                .addQueryParam(
                  Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER,
                  "FormClient"
                ),
              body = Body.fromURLEncodedForm(
                Form.fromStrings(
                  "username" -> "1",
                  "password" -> "1"
                )
              )
            )
          )

          authorizedLogin <- loginRequest("FormClient", sessionCookies)

          protectedResponse <- allRoutes(
            Request(
              method = Method.GET,
              headers = Headers(sessionCookies),
              url = URL.root
            )
          )

        } yield assert(protectedResponse.status)(equalTo(Status.Ok)))
          .provide(defaultLayers)
      }
    )
  }

}
