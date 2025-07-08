package me.seroperson.zio.http.pac4j

import zio._
import zio.http._
import org.pac4j.core.config.Config
import org.pac4j.core.engine.{
  DefaultSecurityLogic,
  DefaultCallbackLogic,
  DefaultLogoutLogic,
  SecurityGrantedAccessAdapter
}
import org.pac4j.core.profile.UserProfile
import org.pac4j.core.context.{FrameworkParameters, WebContext}
import java.util.Collection
import scala.jdk.CollectionConverters._
import org.pac4j.core.adapter.FrameworkAdapter
import zio.http.Status.NotFound
import org.pac4j.core.client.finder.DefaultCallbackClientFinder

object Pac4jMiddleware {

  def middleware(
      config: SecurityConfig,
      isProtgected: Boolean
  ): HandlerAspect[SessionStore, SecurityContext] =
    HandlerAspect.customAuthProvidingZIO(
      provide = (request: Request) => {
        (for {
          _ <- ZIO.logInfo("Got AUTHENTICATION Middleware")
          sessionStore <- ZIO.service[SessionStore]
          pac4jConfig <- ZIO.succeed(buildPac4jConfig(config))
          securityLogic <- ZIO
            .succeed {
              FrameworkAdapter.INSTANCE.applyDefaultSettingsIfUndefined(
                pac4jConfig
              )
              pac4jConfig.getSecurityLogic()
            }
          result <- securityLogic
            .perform(
              pac4jConfig.withWebContextFactory(_ =>
                ZioHttpWebContext(request, sessionStore)
              ),
              new ZioHttpSecurityAdapter,
              Option.empty[String].orNull,
              Option.empty[String].orNull,
              Option.empty[String].orNull,
              null
            ) match {
            case x: ZioHttpWebContext =>
              val sessionId = extractSessionId(request)

              for {
                securityContext <- sessionId match {
                  case Some(id) =>
                    for {
                      _ <- ZIO.logInfo(s"Accessing ${id}")
                      sessionData = zio.Unsafe.unsafe { implicit unsafe =>
                        zio.Runtime.default.unsafe
                          .run(sessionStore.get(id))
                          .getOrThrow()
                      }
                      _ <- ZIO.logInfo(s"Got SessionData: ${sessionData}")
                      userProfile = sessionData.flatMap(
                        _.get("userProfile").collect {
                          case profile: UserProfile => profile
                        }
                      )
                      _ <- ZIO.logInfo(s"Got User: ${userProfile}")
                    } yield SecurityContext(
                      profile = userProfile,
                      sessionId = Some(id),
                      request = request
                    )

                  case None =>
                    ZIO.succeed(
                      SecurityContext(
                        profile = None,
                        sessionId = None,
                        request = request
                      )
                    )
                }
                _ <- ZIO.logInfo(s"Authenticate: ${securityContext}")
              } yield Option(securityContext)
            case y: ZIO[Any, Nothing, Response] =>
              val x = y.flatMap { response =>
                ZIO.fail(response)
              }
              x
          }
        } yield result).logError
      }
    )

  def callback(
      config: SecurityConfig,
      defaultUrl: String = "/",
      renewSession: Boolean = false
  ): Routes[SessionStore, Nothing] = {
    Routes(
      Method.GET / "callback" -> handler { (req: Request) =>
        (for {
          _ <- ZIO.logInfo("Got CALLBACK")
          sessionStore <- ZIO.service[SessionStore]
          pac4jConfig <- ZIO.attempt(buildPac4jConfig(config))
          callbackLogic <- ZIO
            .attempt {
              FrameworkAdapter.INSTANCE.applyDefaultSettingsIfUndefined(
                pac4jConfig
              )
              pac4jConfig.getCallbackLogic()
            }
          response <- callbackLogic
            .perform(
              pac4jConfig.withWebContextFactory(_ =>
                ZioHttpWebContext(req, sessionStore)
              ),
              defaultUrl,
              renewSession,
              /* defaultClient*/ null,
              null
            )
            .asInstanceOf[ZIO[Any, Nothing, Response]]
          _ <- ZIO.logInfo(s"Got CALLBACK response: ${response}")
        } yield response).logError.orDie
      }
    )
  }

  def logout(
      config: SecurityConfig,
      defaultUrl: String = "/",
      logoutUrlPattern: Option[String] = None,
      localLogout: Boolean = true,
      destroySession: Boolean = true,
      centralLogout: Boolean = false
  ): Routes[SessionStore, Nothing] = {
    Routes(
      Method.GET / "logout" -> handler { (req: Request) =>
        (for {
          _ <- ZIO.logInfo("Got AUTHENTICATION Middleware")
          sessionStore <- ZIO.service[SessionStore]
          pac4jConfig <- ZIO.attempt(buildPac4jConfig(config))
          logoutLogic <- ZIO.attempt {
            FrameworkAdapter.INSTANCE.applyDefaultSettingsIfUndefined(
              pac4jConfig
            )
            pac4jConfig.getLogoutLogic()
          }
          response <- logoutLogic
            .perform(
              pac4jConfig.withWebContextFactory(_ =>
                ZioHttpWebContext(req, sessionStore)
              ),
              defaultUrl,
              logoutUrlPattern.orNull,
              localLogout,
              destroySession,
              centralLogout,
              null
            )
            .asInstanceOf[ZIO[Any, Nothing, Response]]
          _ <- ZIO.logInfo(s"Got LOGOUT response: ${response}")
        } yield response).logError.orDie
      }
    )
  }

  private def extractSessionId(request: Request): Option[String] = {
    request.cookie("JSESSIONID").map(_.content)
  }

  private def createDemoProfile(clientName: String): UserProfile = {
    import org.pac4j.core.profile.CommonProfile
    val profile = new CommonProfile()

    clientName match {
      case "GoogleOidcClient" =>
        profile.setId("google-demo-user-123")
        profile.addAttribute("email", "demo.user@gmail.com")
        profile.addAttribute("name", "Demo User")
        profile.addAttribute("provider", "Google")
        profile.addAttribute("role", "user")
      case "GitHubClient" =>
        profile.setId("github-demo-user-456")
        profile.addAttribute("email", "demo.user@company.com")
        profile.addAttribute("name", "Demo Developer")
        profile.addAttribute("provider", "GitHub")
        profile.addAttribute("role", "admin")
      case _ =>
        profile.setId("demo-user-789")
        profile.addAttribute("email", "demo.user@example.org")
        profile.addAttribute("name", "Demo Person")
        profile.addAttribute("provider", "Unknown")
        profile.addAttribute("role", "user")
    }

    profile
  }

  private def buildPac4jConfig(securityConfig: SecurityConfig): Config = {
    val config = new Config()

    config.setSessionStoreFactory(ZioHttpSessionStoreFactory)
    config.setHttpActionAdapter(new ZioHttpActionAdapter)
    config.setSecurityLogic(new DefaultSecurityLogic())
    config.setCallbackLogic(new DefaultCallbackLogic())
    config.setLogoutLogic(new DefaultLogoutLogic())

    securityConfig.clients.foreach { client =>
      config.addClient(client)
    }

    config
  }

}

object ZioHttpFrameworkParameters extends FrameworkParameters {}

class ZioHttpSecurityAdapter extends SecurityGrantedAccessAdapter {
  override def adapt(
      context: WebContext,
      sessionStore: org.pac4j.core.context.session.SessionStore,
      profiles: Collection[UserProfile]
  ): AnyRef = {
    // Store profiles in the context for later retrieval
    context match {
      case zioContext: ZioHttpWebContext =>
        Some(zioContext)
      case _ =>
        None
    }
  }
}

class ZioHttpCallbackAdapter extends SecurityGrantedAccessAdapter {
  override def adapt(
      context: WebContext,
      sessionStore: org.pac4j.core.context.session.SessionStore,
      profiles: Collection[UserProfile]
  ): AnyRef = {
    context match {
      case zioContext: ZioHttpWebContext =>
        zioContext.setResponseStatus(302) // Redirect after successful callback
        zioContext
      case _ =>
        context
    }
  }
}

class ZioHttpLogoutAdapter extends SecurityGrantedAccessAdapter {
  override def adapt(
      context: WebContext,
      sessionStore: org.pac4j.core.context.session.SessionStore,
      profiles: Collection[UserProfile]
  ): AnyRef = {
    context match {
      case zioContext: ZioHttpWebContext =>
        zioContext.setResponseStatus(302)
        zioContext
      case _ =>
        context
    }
  }
}
