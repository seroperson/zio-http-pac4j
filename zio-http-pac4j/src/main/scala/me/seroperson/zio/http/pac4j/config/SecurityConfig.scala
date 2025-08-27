package me.seroperson.zio.http.pac4j.config

import org.pac4j.core.client.Client
import zio.http.Cookie.SameSite
import zio.http.URL

case class SecurityConfig(
    clients: List[Client],
    sessionCookie: SessionCookieConfig = SessionCookieConfig(),
    callback: CallbackConfig = CallbackConfig(),
    login: LoginConfig = LoginConfig(),
    logout: LogoutConfig = LogoutConfig()
)

case class SessionCookieConfig(
    maxAge: Option[Int] = None,
    domain: Option[String] = None,
    path: Option[URL] = Some(URL.root),
    secure: Boolean = false,
    httpOnly: Boolean = false,
    sameSite: Option[SameSite] = None
)

case class LoginConfig(
    defaultUrl: URL = URL.root
)

case class CallbackConfig(
    defaultUrl: URL = URL.root,
    renewSession: Boolean = false,
    defaultClient: String = null
)

/*
 * @param defaultUrl the default url
 * @param logoutUrlPattern the logout url pattern, redirects there on logout
 * @param localLogout whether a local logout is required
 * @param destroySession whether the web session must be destroyed
 * @param centralLogout whether a central logout is required
 * */
case class LogoutConfig(
    defaultUrl: URL = URL.root,
    logoutUrlPattern: Option[String] = Some("api/logout"),
    localLogout: Boolean = true,
    destroySession: Boolean = true,
    centralLogout: Boolean = false
)
