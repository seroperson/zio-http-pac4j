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

case class LogoutConfig(
    defaultUrl: String = "/",
    logoutUrlPattern: Option[String] = None,
    localLogout: Boolean = true,
    destroySession: Boolean = true,
    centralLogout: Boolean = false
)
