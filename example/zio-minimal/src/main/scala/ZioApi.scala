import zio._
import zio.http._
import zio.http.template._
import zio.logging.consoleLogger
import me.seroperson.zio.http.pac4j._
import org.pac4j.oauth.client.Google2Client
import zio.logging.slf4j.bridge.Slf4jBridge
import zio.logging.LogFilter
import org.pac4j.oauth.client.GitHubClient
import org.pac4j.core.profile.CommonProfile
import me.seroperson.zio.http.pac4j.session.InMemorySessionRepository
import me.seroperson.zio.http.pac4j.adapter.ZioHttpActionAdapter
import me.seroperson.zio.http.pac4j.adapter.ZioLogic
import me.seroperson.zio.http.pac4j.session.ZioSessionStore
import me.seroperson.zio.http.pac4j.config.SecurityConfig
import me.seroperson.zio.http.pac4j.adapter.ZioSecurityGrantedAccess
import me.seroperson.zio.http.pac4j.config.SessionCookieConfig
import me.seroperson.zio.http.pac4j.config.CallbackConfig
import me.seroperson.zio.http.pac4j.config.LogoutConfig
import zio.logging.ConsoleLoggerConfig
import org.pac4j.core.profile.UserProfile

object ZioApi extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >+>
      Slf4jBridge.initialize >+>
      consoleLogger(
        ConsoleLoggerConfig.default.copy(filter =
          LogFilter.LogLevelByNameConfig.default
            .copy(rootLevel = LogLevel.Debug)
        )
      )

  val userRoutes = Routes(
    Method.GET / Root -> handler(
      Response.html(
        html(
          head(
            title("Minimal Authentication Example")
          ),
          body(
            h1("zio-http-pac4j Minimal Example"),
            ol(
              li(
                a(
                  href := "/api/login?provider=Google2Client",
                  "Login using Google"
                )
              ),
              li(
                a(
                  href := "/api/login?provider=GitHubClient",
                  "Login using GitHub"
                )
              ),
              li(
                a(href := "/profile", "Protected profile")
              ),
              li(
                a(href := "/logout", "Logout")
              )
            )
          )
        )
      )
    ),
    Method.GET / "profile" -> Handler.fromFunctionZIO { (req: Request) =>
      for {
        profile <- ZIO.service[UserProfile].map(_.asInstanceOf[CommonProfile])
        response <- ZIO.succeed(
          Response.html(
            html(
              head(
                title("User Profile")
              ),
              body(
                h1("User Profile"),
                p(s"Status: ✅ Authenticated user: $profile"),
                p(
                  a(href := "/", "Back to Home"),
                  " | ",
                  a(href := "/api/logout", "Logout")
                )
              )
            )
          )
        )
      } yield response
    } @@ Pac4jMiddleware.errorIfUnauthorized
  )

  val allRoutes =
    (Pac4jMiddleware.callback ++
      Pac4jMiddleware.logout ++
      Pac4jMiddleware.login(clients = List("Google2Client", "GitHubClient")) ++
      userRoutes)

  override val run = for {
    _ <- ZIO.logInfo("Starting on http://localhost:9000")
    baseUrl <- ZIO.succeed(
      sys.env.getOrElse("BASE_URL", "http://localhost:9000")
    )
    _ <- Server
      .serve(allRoutes)
      .provide(
        Server.defaultWithPort(9000),
        ZioPac4jDefaults.live,
        ZLayer.succeed {
          SecurityConfig(
            clients = List(
              {
                new Google2Client(
                  sys.env.get("GOOGLE_CLIENT_ID").getOrElse(""),
                  sys.env.get("GOOGLE_CLIENT_SECRET").getOrElse("")
                ).setCallbackUrl(s"$baseUrl/api/callback")
              }, {
                new GitHubClient(
                  sys.env.get("GITHUB_CLIENT_ID").getOrElse(""),
                  sys.env.get("GITHUB_CLIENT_SECRET").getOrElse("")
                ).setCallbackUrl(s"$baseUrl/api/callback")
              }
            ),
            sessionCookie = SessionCookieConfig(),
            callback = CallbackConfig(),
            logout = LogoutConfig()
          )
        }
      )
  } yield ()
}
