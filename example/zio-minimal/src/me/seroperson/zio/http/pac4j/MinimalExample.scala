package me.seroperson.zio.http.pac4j

import zio._
import zio.http._
import zio.logging.consoleLogger
import me.seroperson.zio.http.pac4j._
import org.pac4j.oauth.client.Google2Client
import zio.logging.slf4j.bridge.Slf4jBridge
import zio.logging.LogFilter
import org.pac4j.oauth.client.GitHubClient

object MinimalExample extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers ++ Runtime
      .addLogger(
        ZLogger.default
          .map(println(_))
          .filterLogLevel(_ >= LogLevel.Debug)
      ) >+> Slf4jBridge.init(LogFilter.logLevel(LogLevel.Debug))

  val securityConfig = {
    val googleClient = new Google2Client(
      "",
      ""
    )
    googleClient.setCallbackUrl("http://localhost:8080/callback")

    val githubClient = new GitHubClient(
      "",
      ""
    )
    githubClient.setCallbackUrl("http://localhost:8080/callback")

    SecurityConfig(
      clients = List( /*googleClient,*/ githubClient)
    )
  }

  val authMiddleware =
    Pac4jMiddleware.middleware(securityConfig, isProtgected = false)

  import zio.http.template._
  val publicRoutes = Routes(
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
                a(href := "/auth/login", "Initiate Google Login")
              ),
              li(
                a(href := "/callback", "Callback handler")
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
    Method.GET / "auth" / "login" -> handler { (req: Request) =>
      val clientNames = securityConfig.clients.map(_.getName).mkString(", ")
      Response.html(
        html(
          head(
            title("Login")
          ),
          body(
            h1("Login"),
            p(
              "In a real implementation, Pac4jMiddleware.authenticate() would redirect to the OAuth provider."
            ),
            p(
              a(href := "/", "Back to Home"),
              " | ",
              a(href := "/callback", "Simulate Callback")
            )
          )
        )
      )
    }
  )

  // Protected routes using the authentication middleware
  val protectedRoutes = {
    val baseRoutes = Routes(
      Method.GET / "profile" -> Handler.fromFunctionZIO { (req: Request) =>
        for {
          _ <- ZIO.logDebug("Inside of route")
          context <- ZIO.service[SecurityContext]
          profileInfo = context.profile match {
            case Some(user) =>
              s"✅ Authenticated user: ${user.getId}"
            case None =>
              "✖️ No user"
          }
          response <- ZIO.succeed(
            Response.html(
              html(
                head(
                  title("User Profile")
                ),
                body(
                  h1("User Profile"),
                  p(s"Status: $profileInfo"),
                  p(
                    a(href := "/", "Back to Home"),
                    " | ",
                    a(href := "/logout", "Logout")
                  )
                )
              )
            )
          )
        } yield response
      }
    ) @@ authMiddleware

    baseRoutes
  }

  val allRoutes =
    publicRoutes ++ Pac4jMiddleware.callback(securityConfig) ++ Pac4jMiddleware
      .logout(securityConfig) ++ protectedRoutes

  override val run = for {
    _ <- ZIO.logDebug("Starting Minimal Example on http://localhost:8080")
    _ <- Server
      .serve(allRoutes)
      .provide(Server.default, SessionStore.live)
  } yield ()
}
