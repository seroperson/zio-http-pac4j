package me.seroperson.zio.http.pac4j

import java.util.Collection
import me.seroperson.zio.http.pac4j.adapter.ZioHttpActionAdapter
import me.seroperson.zio.http.pac4j.adapter.ZioSecurityGrantedAccess
import me.seroperson.zio.http.pac4j.adapter.ZioWebContext
import me.seroperson.zio.http.pac4j.config.SecurityConfig
import me.seroperson.zio.http.pac4j.session.InMemorySessionRepository
import me.seroperson.zio.http.pac4j.session.SessionRepository
import me.seroperson.zio.http.pac4j.session.ZioSessionStore
import org.pac4j.core.adapter.FrameworkAdapter
import org.pac4j.core.client.finder.DefaultCallbackClientFinder
import org.pac4j.core.config.Config
import org.pac4j.core.context.FrameworkParameters
import org.pac4j.core.context.WebContext
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.context.session.SessionStoreFactory
import org.pac4j.core.engine.CallbackLogic
import org.pac4j.core.engine.DefaultCallbackLogic
import org.pac4j.core.engine.DefaultLogoutLogic
import org.pac4j.core.engine.DefaultSecurityLogic
import org.pac4j.core.engine.LogoutLogic
import org.pac4j.core.engine.SecurityGrantedAccessAdapter
import org.pac4j.core.engine.SecurityLogic
import org.pac4j.core.http.adapter.HttpActionAdapter
import org.pac4j.core.profile.CommonProfile
import org.pac4j.core.profile.ProfileManager
import org.pac4j.core.profile.UserProfile
import org.pac4j.core.profile.factory.ProfileManagerFactory
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import zio._
import zio.http._
import zio.http.Status.NotFound

object Pac4jMiddleware {

  private def nullIfEmpty(value: String) = if (value.isEmpty) null else value

  def errorIfUnauthorized =
    HandlerAspect.customAuthZIO(
      verify = (req: Request) =>
        (for {
          pac4jConfig <- buildPac4jConfig(req)
          webContext <- buildPac4jWebContext(pac4jConfig)
          sessionStore <- buildPac4jSessionStore(pac4jConfig)
          profileManager <- buildPac4jProfileManager(
            pac4jConfig,
            webContext,
            sessionStore
          )
          profile = profileManager.getProfile.toScala
        } yield profile.isDefined).mapError { _ =>
          Response.internalServerError
        }.logError
    )

  def errorIfUnauthorizedWithProfile =
    HandlerAspect.customAuthProvidingZIO(
      provide = (req: Request) =>
        (for {
          pac4jConfig <- buildPac4jConfig(req)
          webContext <- buildPac4jWebContext(pac4jConfig)
          sessionStore <- buildPac4jSessionStore(pac4jConfig)
          profileManager <- buildPac4jProfileManager(
            pac4jConfig,
            webContext,
            sessionStore
          )
          profile = profileManager.getProfile.toScala
        } yield profile).mapError { _ =>
          Response.internalServerError
        }.logError
    )

  def securityFilter(
      clients: List[String] = List.empty,
      authorizers: List[String] = List.empty,
      matchers: List[String] = List.empty
  ) =
    HandlerAspect.customAuthProvidingZIO(
      provide = (req: Request) =>
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
              (for {
                sessionStore <- buildPac4jSessionStore(pac4jConfig)
                profileManager <- buildPac4jProfileManager(
                  pac4jConfig,
                  context,
                  sessionStore
                )
              } yield profileManager.getProfile.toScala).mapError { _ =>
                Response.internalServerError
              }

            case y: Response =>
              ZIO.fail(y)
          }
        } yield result).logError
    )

  def login[A](
      clients: List[String] = List.empty,
      authorizers: List[String] = List.empty,
      matchers: List[String] = List.empty,
      route: RoutePattern[A] = Method.GET / "api" / "login"
  ) =
    Routes(
      route -> handler { (req: Request) =>
        (for {
          securityConfig <- ZIO.service[SecurityConfig]
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
              ZIO.succeed(
                Response.redirect(
                  securityConfig.login.defaultUrl,
                  isPermanent = false
                )
              )

            case err: Response =>
              ZIO.succeed(err)
          }
        } yield result)
      }
    )

  def callback[A](
      routes: Chunk[RoutePattern[A]] = Chunk(
        Method.GET / "api" / "callback",
        Method.POST / "api" / "callback"
      )
  ) = {
    val callbackHandler = handler { (req: Request) =>
      (for {
        config <- ZIO.service[SecurityConfig]
        pac4jConfig <- buildPac4jConfig(req)
        response <- ZIO
          .attempt(
            pac4jConfig
              .getCallbackLogic()
              .perform(
                pac4jConfig,
                config.callback.defaultUrl.encode,
                config.callback.renewSession,
                config.callback.defaultClient,
                null: FrameworkParameters
              )
              .asInstanceOf[Response]
          )
      } yield response).mapError { _ =>
        Response.internalServerError
      }.logError
    }

    Routes(routes.map { route =>
      route -> callbackHandler
    })
  }

  def logout[A](route: RoutePattern[A] = Method.GET / "api" / "logout") =
    Routes(
      route -> handler { (req: Request) =>
        (for {
          config <- ZIO.service[SecurityConfig]
          pac4jConfig <- buildPac4jConfig(req)
          response = pac4jConfig
            .getLogoutLogic()
            .perform(
              pac4jConfig,
              config.logout.defaultUrl.encode,
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

  def buildPac4jConfig(req: Request) =
    for {
      securityConfig <- ZIO.service[SecurityConfig]
      sessionRepository <- ZIO.service[SessionRepository]
      httpActionAdapter <- ZIO.service[HttpActionAdapter]
      securityLogic <- ZIO.service[SecurityLogic]
      callbackLogic <- ZIO.service[CallbackLogic]
      logoutLogic <- ZIO.service[LogoutLogic]
      sessionStore <- ZIO.service[SessionStore]
      pac4jConfig <- ZIO.succeed {
        val pac4jConfig = new Config()
        pac4jConfig.setSessionStoreFactory(
          (parameters: FrameworkParameters) => {
            sessionStore
          }
        )
        pac4jConfig.setHttpActionAdapter(httpActionAdapter)
        pac4jConfig.setSecurityLogic(securityLogic)
        pac4jConfig.setCallbackLogic(callbackLogic)
        pac4jConfig.setLogoutLogic(logoutLogic)
        pac4jConfig.setProfileManagerFactory(ProfileManagerFactory.DEFAULT)
        pac4jConfig.setWebContextFactory(_ => new ZioWebContext(req))

        securityConfig.clients.foreach { client =>
          pac4jConfig.addClient(client)
        }

        securityConfig.authorizers.foreach { case (name, authorizer) =>
          pac4jConfig.addAuthorizer(name, authorizer)
        }

        pac4jConfig
      }
    } yield pac4jConfig

  def buildPac4jWebContext(pac4jConfig: Config) = ZIO.attempt(
    pac4jConfig
      .getWebContextFactory()
      .newContext(null: FrameworkParameters)
  )

  def buildPac4jSessionStore(pac4jConfig: Config) = ZIO.attempt(
    pac4jConfig
      .getSessionStoreFactory()
      .newSessionStore(null: FrameworkParameters)
  )

  def buildPac4jProfileManager(
      pac4jConfig: Config,
      webContext: WebContext,
      sessionStore: SessionStore
  ) = ZIO
    .attempt {
      pac4jConfig
        .getProfileManagerFactory()
        .apply(webContext, sessionStore)
    }

}
