package me.seroperson.zio.http.pac4j.config

import org.pac4j.core.authorization.authorizer.Authorizer
import org.pac4j.core.client.Client
import zio.http.Cookie.SameSite
import zio.http.URL

/** Main security configuration for the PAC4J ZIO HTTP integration.
  *
  * @param clients
  *   List of PAC4J authentication clients (e.g., OAuth, SAML, etc.)
  * @param authorizers
  *   List of named authorizers for access control, each consisting of a name
  *   and the authorizer implementation
  * @param sessionCookie
  *   Configuration for session cookies
  * @param callback
  *   Configuration for the authentication callback handling
  * @param login
  *   Configuration for the login process
  * @param logout
  *   Configuration for the logout process
  */
case class SecurityConfig(
    clients: List[Client] = List.empty,
    authorizers: List[(String, Authorizer)] = List.empty,
    sessionCookie: SessionCookieConfig = SessionCookieConfig(),
    callback: CallbackConfig = CallbackConfig(),
    login: LoginConfig = LoginConfig(),
    logout: LogoutConfig = LogoutConfig()
)

/** Configuration for session cookies used by the authentication system.
  *
  * @param maxAge
  *   Maximum age of the cookie in seconds. None means session cookie (expires
  *   when browser closes)
  * @param domain
  *   Domain scope for the cookie. None means current domain only
  * @param path
  *   Path scope for the cookie. Defaults to root path
  * @param secure
  *   Whether the cookie should only be sent over HTTPS connections
  * @param httpOnly
  *   Whether the cookie should be accessible only via HTTP (not JavaScript)
  * @param sameSite
  *   SameSite attribute for CSRF protection. Controls when cookies are sent
  *   with cross-site requests
  */
case class SessionCookieConfig(
    maxAge: Option[Int] = None,
    domain: Option[String] = None,
    path: Option[URL] = Some(URL.root),
    secure: Boolean = false,
    httpOnly: Boolean = false,
    sameSite: Option[SameSite] = None
)

/** Configuration for the login process.
  *
  * @param defaultUrl
  *   The default URL to redirect to after successful login
  */
case class LoginConfig(
    defaultUrl: URL = URL.root
)

/** Configuration for the authentication callback handling.
  *
  * @param defaultUrl
  *   The default URL to redirect to after successful authentication callback
  * @param renewSession
  *   Whether to renew the session after successful authentication
  * @param defaultClient
  *   The default client to use when none is specified in the callback request
  */
case class CallbackConfig(
    defaultUrl: URL = URL.root,
    renewSession: Boolean = false,
    defaultClient: String = null
)

/** Configuration for the logout process.
  *
  * @param defaultUrl
  *   The default URL to redirect to after logout
  * @param logoutUrlPattern
  *   The logout URL pattern that triggers logout when accessed
  * @param localLogout
  *   Whether to perform local logout (clearing local session)
  * @param destroySession
  *   Whether to completely destroy the web session
  * @param centralLogout
  *   Whether to perform central logout (notifying the identity provider)
  */
case class LogoutConfig(
    defaultUrl: URL = URL.root,
    logoutUrlPattern: Option[String] = Some("api/logout"),
    localLogout: Boolean = true,
    destroySession: Boolean = true,
    centralLogout: Boolean = false
)
