package me.seroperson.zio.http.pac4j

import zio._
import zio.http._
import zio.logging.consoleLogger
import me.seroperson.zio.http.pac4j._
import org.pac4j.oauth.client.Google2Client
import zio.logging.slf4j.bridge.Slf4jBridge
import zio.logging.LogFilter
import org.pac4j.oauth.client.GitHubClient
import org.pac4j.core.profile.CommonProfile
import me.seroperson.zio.http.pac4j.session.InMemorySessionRepository

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
      "765286948072-prc9dq0j47j5onf72m936fe509qnbllh.apps.googleusercontent.com",
      "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"
    )
    googleClient.setCallbackUrl("http://localhost:8080/callback")

    val githubClient = new GitHubClient(
      "Ov23lirOtiGTMrSSQOX7",
      "c80fcb7e2ee8f674f5873e1fea0ec91114e4224f"
    )
    githubClient.setCallbackUrl("http://localhost:8080/callback")

    SecurityConfig(
      clients = List(googleClient, githubClient)
    )
  }

  val authMiddleware =
    Pac4jMiddleware.middleware(securityConfig, isProtected = false)

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
                a(href := "/profile", "Protected profile")
              ),
              li(
                a(href := "/logout", "Logout")
              )
            )
          )
        )
      )
    )
  )

  // Protected routes using the authentication middleware
  val protectedRoutes = {
    val baseRoutes = Routes(
      Method.GET / "profile" -> Handler.fromFunctionZIO { (req: Request) =>
        for {
          _ <- ZIO.logDebug("Inside of route")
          profile <- ZIO.service[List[CommonProfile]].map(_.headOption)
          profileInfo = profile match {
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
      .provide(Server.default, InMemorySessionRepository.live)
  } yield ()
}
