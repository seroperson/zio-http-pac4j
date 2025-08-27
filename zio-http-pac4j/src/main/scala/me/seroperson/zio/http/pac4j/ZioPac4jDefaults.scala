package me.seroperson.zio.http.pac4j

import zio.ZLayer
import me.seroperson.zio.http.pac4j.session.InMemorySessionRepository
import me.seroperson.zio.http.pac4j.adapter.ZioSecurityGrantedAccess
import me.seroperson.zio.http.pac4j.adapter.ZioHttpActionAdapter
import me.seroperson.zio.http.pac4j.adapter.ZioLogic
import me.seroperson.zio.http.pac4j.session.ZioSessionStore

object ZioPac4jDefaults {

  lazy val live = ZioSecurityGrantedAccess.live >+>
    ZioHttpActionAdapter.live >+>
    ZioLogic.callbackLogic >+>
    ZioLogic.logoutLogic >+>
    ZioLogic.securityLogic >+>
    ZioSessionStore.live

}
