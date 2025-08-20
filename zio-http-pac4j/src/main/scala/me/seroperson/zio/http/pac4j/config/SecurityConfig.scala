package me.seroperson.zio.http.pac4j.config

import org.pac4j.core.client.Client
import zio.http.Cookie.SameSite

case class SecurityConfig(
    clients: List[Client],
    sessionCookie: SessionCookieConfig,
    callback: CallbackConfig,
    logout: LogoutConfig
)

case class SessionCookieConfig(
    maxAge: Option[Int] = None,
    domain: Option[String] = None,
    path: Option[String] = Some("/"),
    secure: Boolean = false,
    httpOnly: Boolean = false,
    sameSite: Option[SameSite] = None
)

case class CallbackConfig(
    defaultUrl: String = "/",
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
    defaultUrl: String = "/",
    logoutUrlPattern: Option[String] = Some("api/logout"),
    localLogout: Boolean = true,
    destroySession: Boolean = true,
    centralLogout: Boolean = false
)
