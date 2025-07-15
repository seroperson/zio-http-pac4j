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
import org.pac4j.core.context.session.SessionStoreFactory
import org.pac4j.core.context
import me.seroperson.zio.http.pac4j.session.ZioSessionStore
import me.seroperson.zio.http.pac4j.session.InMemorySessionRepository
import org.pac4j.core.profile.ProfileManager
import org.pac4j.core.profile.CommonProfile
import org.pac4j.core.profile.factory.ProfileManagerFactory
import me.seroperson.zio.http.pac4j.session.SessionRepository

object Pac4jMiddleware {

  def middleware(
      config: SecurityConfig,
      isProtected: Boolean
  ) /*: HandlerAspect[Any, List[CommonProfile]]*/ =
    HandlerAspect.customAuthProvidingZIO(
      provide = (request: Request) => {
        (for {
          _ <- ZIO.logInfo("Got AUTHENTICATION Middleware")
          pac4jConfig <- buildPac4jConfig(config)
          securityLogic <- ZIO
            .succeed {
              pac4jConfig.getSecurityLogic()
            }
          result <- securityLogic
            .perform(
              pac4jConfig.withWebContextFactory(_ =>
                new ZioHttpWebContext(request)
              ),
              new ZioHttpSecurityAdapter,
              Option.empty[String].orNull,
              Option.empty[String].orNull,
              Option.empty[String].orNull,
              null
            ) match {
            case Some(x: ZioHttpWebContext) =>
              val manager = new ProfileManager(
                x,
                pac4jConfig.getSessionStoreFactory.newSessionStore(null)
              )
              val profiles = manager.getProfiles.asScala
                .map(_.asInstanceOf[CommonProfile])
                .toList
              ZIO.succeed(Option(profiles))

            case y: Response =>
              ZIO.fail(y)
          }
        } yield result).logError
      }
    )

  def callback(
      config: SecurityConfig,
      defaultUrl: String = "/",
      renewSession: Boolean = false
  ) = {
    Routes(
      Method.GET / "callback" -> handler { (req: Request) =>
        (for {
          _ <- ZIO.logInfo("Got CALLBACK")
          pac4jConfig <- buildPac4jConfig(config)
          callbackLogic <- ZIO
            .attempt {
              pac4jConfig.getCallbackLogic()
            }
          response <- ZIO.succeed(
            callbackLogic
              .perform(
                pac4jConfig.withWebContextFactory(_ =>
                  new ZioHttpWebContext(req)
                ),
                defaultUrl,
                renewSession,
                /* defaultClient*/ null,
                null
              )
              .asInstanceOf[Response]
          )
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
  ) = {
    Routes(
      Method.GET / "logout" -> handler { (req: Request) =>
        (for {
          _ <- ZIO.logInfo("Got AUTHENTICATION Middleware")
          pac4jConfig <- buildPac4jConfig(config)
          logoutLogic <- ZIO.attempt {
            pac4jConfig.getLogoutLogic()
          }
          response = logoutLogic
            .perform(
              pac4jConfig.withWebContextFactory(_ =>
                new ZioHttpWebContext(req)
              ),
              defaultUrl,
              logoutUrlPattern.orNull,
              localLogout,
              destroySession,
              centralLogout,
              null
            )
            .asInstanceOf[Response]
          _ <- ZIO.logInfo(s"Got LOGOUT response: ${response}")
        } yield response).logError.orDie
      }
    )
  }

  private def buildPac4jConfig(securityConfig: SecurityConfig) = {

    for {
      sessionRepository <- ZIO.service[SessionRepository]
      config <- ZIO.succeed {
        val config = new Config()
        config.setSessionStoreFactory((parameters: FrameworkParameters) => {
          new ZioSessionStore(
            sessionRepository,
            Runtime.default
          )()
        })
        config.setHttpActionAdapter(new ZioHttpActionAdapter)
        config.setSecurityLogic(new DefaultSecurityLogic())
        config.setCallbackLogic(new DefaultCallbackLogic())
        config.setLogoutLogic(new DefaultLogoutLogic())
        config.setProfileManagerFactory(ProfileManagerFactory.DEFAULT)

        securityConfig.clients.foreach { client =>
          config.addClient(client)
        }
        config
      }
    } yield config
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
