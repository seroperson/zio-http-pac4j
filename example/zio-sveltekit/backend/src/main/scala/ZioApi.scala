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
import org.pac4j.core.engine.savedrequest.DefaultSavedRequestHandler
import org.pac4j.core.profile.UserProfile
import zio.logging.consoleLogger
import zio.logging.ConsoleLoggerConfig

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
    Method.GET / "api" / "profile" -> handler { (req: Request) =>
      for {
        profile <- ZIO.service[UserProfile].map(_.asInstanceOf[CommonProfile])
        response <- ZIO.succeed(
          Response.json(
            s"""
            {
              "username": "${profile.getUsername}",
              "email": "${profile.getEmail}",
              "avatar": "${profile.getPictureUrl}"
            }
            """
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
        Server.defaultWithPort(8080),
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
