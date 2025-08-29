import me.seroperson.zio.http.pac4j._
import me.seroperson.zio.http.pac4j.adapter.ZioHttpActionAdapter
import me.seroperson.zio.http.pac4j.adapter.ZioLogic
import me.seroperson.zio.http.pac4j.adapter.ZioSecurityGrantedAccess
import me.seroperson.zio.http.pac4j.config.CallbackConfig
import me.seroperson.zio.http.pac4j.config.LoginConfig
import me.seroperson.zio.http.pac4j.config.LogoutConfig
import me.seroperson.zio.http.pac4j.config.SecurityConfig
import me.seroperson.zio.http.pac4j.config.SessionCookieConfig
import me.seroperson.zio.http.pac4j.session.InMemorySessionRepository
import me.seroperson.zio.http.pac4j.session.ZioSessionStore
import org.pac4j.core.authorization.authorizer.IsFullyAuthenticatedAuthorizer
import org.pac4j.core.credentials.authenticator.Authenticator
import org.pac4j.core.profile.CommonProfile
import org.pac4j.core.profile.UserProfile
import org.pac4j.http.client.direct.DirectFormClient
import org.pac4j.http.client.indirect.FormClient
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator
import org.pac4j.oauth.client.GitHubClient
import org.pac4j.oauth.client.Google2Client
import zio._
import zio.http._
import zio.http.template._
import zio.logging.ConsoleLoggerConfig
import zio.logging.LogFilter
import zio.logging.consoleLogger
import zio.logging.slf4j.bridge.Slf4jBridge

object ZioApi extends ZIOAppDefault {

  val baseUrl = sys.env.getOrElse("BASE_URL", "http://localhost:9000")

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
                a(
                  href := "/api/login?provider=FormClient",
                  "Login using a form"
                )
              ),
              li(
                a(href := "/profile", "Protected profile")
              ),
              li(
                a(href := "/api/logout", "Logout")
              )
            )
          )
        )
      )
    ),
    Method.GET / "loginForm" -> handler(
      Response.html(
        html(
          head(
            title("Login using a form")
          ),
          body(
            h1("Login using a form"),
            form(
              actionAttr := s"$baseUrl/api/callback?client_name=FormClient",
              methodAttr := "POST",
              input(
                typeAttr := "text",
                nameAttr := "username",
                valueAttr := ""
              ),
              p(),
              input(
                typeAttr := "password",
                nameAttr := "password",
                valueAttr := ""
              ),
              p(),
              input(
                typeAttr := "submit",
                valueAttr := "Submit"
              )
            )
          )
        )
      )
    ),
    Method.GET / "profile" -> Handler.fromFunctionZIO { (req: Request) =>
      for {
        profile <- ZIO.service[UserProfile /* CommonProfile */ ]
        response <- ZIO.succeed(
          Response.html(
            html(
              head(
                title("User Profile")
              ),
              body(
                h1("User Profile"),
                p(s"Status: ✅ Authenticated user: ${profile}"),
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
    } @@ Pac4jMiddleware.securityFilter(
      clients = List(),
      authorizers = List("IsFullyAuthenticatedAuthorizer")
    )
  )

  val allRoutes =
    (Pac4jMiddleware.callback() ++
      Pac4jMiddleware.logout() ++
      Pac4jMiddleware
        .login(clients = List("Google2Client", "GitHubClient", "FormClient")) ++
      userRoutes)

  override val run = for {
    _ <- ZIO.logInfo(s"Starting on $baseUrl")
    _ <- Server
      .serve(allRoutes)
      .provide(
        Server.defaultWithPort(9000),
        ZioPac4jDefaults.live,
        InMemorySessionRepository.live,
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
              }, {
                new FormClient(
                  /* loginUrl = */ s"$baseUrl/loginForm",
                  new SimpleTestUsernamePasswordAuthenticator()
                ).setCallbackUrl(s"$baseUrl/api/callback")
              }
            ),
            authorizers = List(
              "IsFullyAuthenticatedAuthorizer" -> new IsFullyAuthenticatedAuthorizer()
            )
          )
        }
      )
  } yield ()
}
