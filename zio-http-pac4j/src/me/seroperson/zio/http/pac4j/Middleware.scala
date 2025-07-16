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
import scala.jdk.OptionConverters._
import org.pac4j.core.adapter.FrameworkAdapter
import zio.http.Status.NotFound
import org.pac4j.core.client.finder.DefaultCallbackClientFinder
import org.pac4j.core.context.session.SessionStoreFactory
import org.pac4j.core.context.session.SessionStore
import me.seroperson.zio.http.pac4j.session.ZioSessionStore
import me.seroperson.zio.http.pac4j.session.InMemorySessionRepository
import org.pac4j.core.profile.ProfileManager
import org.pac4j.core.profile.CommonProfile
import org.pac4j.core.profile.factory.ProfileManagerFactory
import me.seroperson.zio.http.pac4j.session.SessionRepository
import me.seroperson.zio.http.pac4j.config.SecurityConfig
import me.seroperson.zio.http.pac4j.adapter.ZioWebContext
import me.seroperson.zio.http.pac4j.adapter.ZioHttpActionAdapter
import me.seroperson.zio.http.pac4j.adapter.ZioSecurityGrantedAccess
import org.pac4j.core.http.adapter.HttpActionAdapter
import org.pac4j.core.engine.SecurityLogic
import org.pac4j.core.engine.CallbackLogic
import org.pac4j.core.engine.LogoutLogic

object Pac4jMiddleware {

  private def nullIfEmpty(value: String) = if (value.isEmpty) null else value

  def errorIfUnauthorized =
    HandlerAspect.customAuthProvidingZIO(
      provide = (req: Request) =>
        (for {
          pac4jConfig <- buildPac4jConfig(req)
          securityGrantedAccess <- ZIO.service[SecurityGrantedAccessAdapter]
          result <- ZIO
            .attempt {
              pac4jConfig
                .getProfileManagerFactory()
                .apply(
                  pac4jConfig
                    .getWebContextFactory()
                    .newContext(null: FrameworkParameters),
                  pac4jConfig
                    .getSessionStoreFactory()
                    .newSessionStore(null: FrameworkParameters)
                )
                .getProfile
                .toScala
            }
            .logError
            .mapError { ex =>
              Response.internalServerError(ex.getMessage)
            }
        } yield result).logError
    )

  def securityFilter(
      clients: List[String] = List.empty,
      authorizers: List[String] = List.empty,
      matchers: List[String] = List.empty
  ) =
    HandlerAspect.updateRequestZIO(
      update = (req: Request) =>
        (for {
          pac4jConfig <- buildPac4jConfig(req)
          securityGrantedAccess <- ZIO.service[SecurityGrantedAccessAdapter]
          result <- pac4jConfig
            .getSecurityLogic()
            .perform(
              pac4jConfig,
              securityGrantedAccess,
              nullIfEmpty(clients.mkString(",")),
              nullIfEmpty(authorizers.mkString(",")),
              nullIfEmpty(matchers.mkString(",")),
              null: FrameworkParameters
            ) match {
            case Some(context: ZioWebContext) =>
              ZIO.succeed(req)

            case y: Response =>
              ZIO.fail(y)
          }
        } yield result).logError
    )

  def login(
      clients: List[String] = List.empty,
      authorizers: List[String] = List.empty,
      matchers: List[String] = List.empty
  ) =
    Routes(
      Method.GET / "api" / "login" -> handler { (req: Request) =>
        (for {
          pac4jConfig <- buildPac4jConfig(req)
          securityGrantedAccess <- ZIO.service[SecurityGrantedAccessAdapter]
          result <- pac4jConfig
            .getSecurityLogic()
            .perform(
              pac4jConfig,
              securityGrantedAccess,
              nullIfEmpty {
                (req.query[String]("provider") match {
                  case Left(_)         => clients
                  case Right(provider) => clients.filter(_ == provider)
                }).mkString(",")
              },
              nullIfEmpty(authorizers.mkString(",")),
              nullIfEmpty(matchers.mkString(",")),
              null: FrameworkParameters
            ) match {
            case Some(context: ZioWebContext) =>
              ZIO.succeed(Response.redirect(URL.root, isPermanent = false))

            case err: Response =>
              ZIO.succeed(err)
          }
        } yield result)
      }
    )

  def callback = {
    val callbackHandler = handler { (req: Request) =>
      (for {
        config <- ZIO.service[SecurityConfig]
        pac4jConfig <- buildPac4jConfig(req)
        response <- ZIO.succeed(
          pac4jConfig
            .getCallbackLogic()
            .perform(
              pac4jConfig,
              config.callback.defaultUrl,
              config.callback.renewSession,
              config.callback.defaultClient,
              null: FrameworkParameters
            )
            .asInstanceOf[Response]
        )
      } yield response)
    }
    Routes(
      Method.GET / "api" / "callback" -> callbackHandler,
      Method.POST / "api" / "callback" -> callbackHandler
    )
  }

  def logout =
    Routes(
      Method.GET / "api" / "logout" -> handler { (req: Request) =>
        (for {
          config <- ZIO.service[SecurityConfig]
          pac4jConfig <- buildPac4jConfig(req)
          response = pac4jConfig
            .getLogoutLogic()
            .perform(
              pac4jConfig,
              config.logout.defaultUrl,
              config.logout.logoutUrlPattern.orNull,
              config.logout.localLogout,
              config.logout.destroySession,
              config.logout.centralLogout,
              null: FrameworkParameters
            )
            .asInstanceOf[Response]
        } yield response)
      }
    )

  private def buildPac4jConfig(req: Request) =
    for {
      securityConfig <- ZIO.service[SecurityConfig]
      sessionRepository <- ZIO.service[SessionRepository]
      httpActionAdapter <- ZIO.service[HttpActionAdapter]
      securityLogic <- ZIO.service[SecurityLogic]
      callbackLogic <- ZIO.service[CallbackLogic]
      logoutLogic <- ZIO.service[LogoutLogic]
      sessionStore <- ZIO.service[SessionStore]
      config <- ZIO.succeed {
        val config = new Config()
        config.setSessionStoreFactory((parameters: FrameworkParameters) => {
          sessionStore
        })
        config.setHttpActionAdapter(httpActionAdapter)
        config.setSecurityLogic(securityLogic)
        config.setCallbackLogic(callbackLogic)
        config.setLogoutLogic(logoutLogic)
        config.setProfileManagerFactory(ProfileManagerFactory.DEFAULT)
        config.setWebContextFactory(_ => new ZioWebContext(req))

        securityConfig.clients.foreach { client =>
          config.addClient(client)
        }

        config
      }
    } yield config
}
