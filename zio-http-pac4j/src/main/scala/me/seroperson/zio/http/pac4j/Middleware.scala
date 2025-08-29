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

/** PAC4J middleware implementation for ZIO HTTP that provides authentication
  * and authorization capabilities through various handler aspects and route
  * definitions.
  *
  * This object contains the main middleware functions for integrating PAC4J
  * security features with ZIO HTTP applications, including authentication
  * filters, login/logout handlers, and callback processing.
  */
object Pac4jMiddleware {

  /** Converts empty strings to null, as required by PAC4J API.
    *
    * @param value
    *   The string value to check
    * @return
    *   null if the string is empty, otherwise the original value
    */
  private def nullIfEmpty(value: String) = if (value.isEmpty) null else value

  /** Creates a handler aspect that verifies if a user is authenticated. Returns
    * an error response if the user is not authenticated.
    *
    * This middleware checks for the presence of an authenticated user profile
    * in the current session and fails with an internal server error if no valid
    * authentication is found.
    *
    * @return
    *   A HandlerAspect that verifies authentication status
    */
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

  /** Creates a handler aspect that verifies authentication and provides the
    * user profile. Returns an error response if the user is not authenticated,
    * otherwise provides the authenticated user's profile to the handler.
    *
    * This middleware not only checks for authentication but also makes the user
    * profile available to the handler logic for further processing.
    *
    * @return
    *   A HandlerAspect that verifies authentication and provides the user
    *   profile
    */
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

  /** Creates a security filter that enforces authentication and authorization
    * rules and provides the authenticated user profile to the handler.
    *
    * This middleware applies PAC4J's security logic with the specified clients,
    * authorizers, and matchers. If security checks pass, it provides the user
    * profile to the handler. If they fail, it returns an appropriate error
    * response.
    *
    * @param clients
    *   List of client names to use for authentication
    * @param authorizers
    *   List of authorizer names to use for authorization
    * @param matchers
    *   List of matcher names to use for conditional security
    * @return
    *   A HandlerAspect that enforces security and provides the user profile
    */
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

  /** Creates a security filter that enforces authentication and authorization
    * rules without providing the user profile to the handler.
    *
    * This middleware applies PAC4J's security logic with the specified clients,
    * authorizers, and matchers. It only verifies that security checks pass but
    * doesn't provide the user profile data to the handler.
    *
    * @param clients
    *   List of client names to use for authentication
    * @param authorizers
    *   List of authorizer names to use for authorization
    * @param matchers
    *   List of matcher names to use for conditional security
    * @return
    *   A HandlerAspect that enforces security without providing user profile
    */
  def securityFilterUnit(
      clients: List[String] = List.empty,
      authorizers: List[String] = List.empty,
      matchers: List[String] = List.empty
  ) =
    HandlerAspect.customAuthZIO(
      verify = (req: Request) =>
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
              ZIO.succeed(true)

            case y: Response =>
              ZIO.fail(y)
          }
        } yield result).logError
    )

  /** Creates login routes that initiate the authentication process.
    *
    * This method creates routes that trigger the authentication flow using
    * PAC4J's security logic. It supports provider selection via query parameter
    * and redirects to the configured default URL upon successful
    * authentication.
    *
    * @param clients
    *   List of client names to use for authentication
    * @param authorizers
    *   List of authorizer names to use for authorization
    * @param matchers
    *   List of matcher names to use for conditional security
    * @param route
    *   The route pattern for the login endpoint (defaults to GET /api/login)
    * @return
    *   Routes containing the login handler
    */
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

  /** Creates callback routes that handle authentication responses from identity
    * providers.
    *
    * This method creates routes that process authentication callbacks from
    * external identity providers (OAuth, SAML, etc.). It supports both GET and
    * POST methods by default and handles the completion of the authentication
    * flow.
    *
    * @param routes
    *   Chunk of route patterns for callback endpoints (defaults to GET and POST
    *   /api/callback)
    * @return
    *   Routes containing the callback handlers
    */
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

  /** Creates logout routes that handle user logout and session termination.
    *
    * This method creates routes that process logout requests, clearing user
    * sessions and optionally performing central logout with identity providers.
    *
    * @param route
    *   The route pattern for the logout endpoint (defaults to GET /api/logout)
    * @return
    *   Routes containing the logout handler
    */
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

  /** Builds a PAC4J configuration from ZIO environment services and request
    * context.
    *
    * This method assembles a complete PAC4J Config object by retrieving all
    * necessary services from the ZIO environment and configuring them
    * appropriately. It sets up session stores, action adapters, security logic,
    * and registers clients and authorizers.
    *
    * @param req
    *   The current HTTP request for context
    * @return
    *   A ZIO effect that produces a configured PAC4J Config
    */
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

  /** Creates a PAC4J WebContext from the configured PAC4J Config.
    *
    * @param pac4jConfig
    *   The PAC4J configuration containing the web context factory
    * @return
    *   A ZIO effect that produces a WebContext
    */
  def buildPac4jWebContext(pac4jConfig: Config) = ZIO.attempt(
    pac4jConfig
      .getWebContextFactory()
      .newContext(null: FrameworkParameters)
  )

  /** Creates a PAC4J SessionStore from the configured PAC4J Config.
    *
    * @param pac4jConfig
    *   The PAC4J configuration containing the session store factory
    * @return
    *   A ZIO effect that produces a SessionStore
    */
  def buildPac4jSessionStore(pac4jConfig: Config) = ZIO.attempt(
    pac4jConfig
      .getSessionStoreFactory()
      .newSessionStore(null: FrameworkParameters)
  )

  /** Creates a PAC4J ProfileManager for managing user profiles.
    *
    * @param pac4jConfig
    *   The PAC4J configuration containing the profile manager factory
    * @param webContext
    *   The web context for the current request
    * @param sessionStore
    *   The session store for profile persistence
    * @return
    *   A ZIO effect that produces a ProfileManager
    */
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
