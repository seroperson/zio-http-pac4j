package me.seroperson.zio.http.pac4j

import me.seroperson.zio.http.pac4j.adapter.ZioHttpActionAdapter
import me.seroperson.zio.http.pac4j.adapter.ZioLogic
import me.seroperson.zio.http.pac4j.adapter.ZioSecurityGrantedAccess
import me.seroperson.zio.http.pac4j.session.InMemorySessionRepository
import me.seroperson.zio.http.pac4j.session.ZioSessionStore
import zio.ZLayer

/** Default ZIO layers for PAC4J integration with ZIO HTTP.
  *
  * This object provides a pre-configured set of ZIO layers that include all the
  * necessary components for PAC4J security integration. It combines security
  * adapters, HTTP action adapters, authentication logic implementations, and
  * session management components.
  *
  * The `live` layer can be used as a starting point for applications that want
  * to use the default PAC4J configuration without custom implementations.
  */
object ZioPac4jDefaults {

  /** A complete ZIO layer stack providing all default PAC4J services.
    *
    * Usage example:
    * {{{
    * import me.seroperson.zio.http.pac4j.ZioPac4jDefaults
    *
    * val app = myRoutes.provide(
    *   ZioPac4jDefaults.live,
    *   // other layers...
    * )
    * }}}
    *
    * @return
    *   A ZLayer that provides all necessary PAC4J services
    */
  lazy val live = ZioSecurityGrantedAccess.live >+>
    ZioHttpActionAdapter.live >+>
    ZioLogic.callbackLogic >+>
    ZioLogic.logoutLogic >+>
    ZioLogic.securityLogic >+>
    ZioSessionStore.live

}
