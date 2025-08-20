package me.seroperson.zio.http.pac4j.adapter

import org.pac4j.core.context.WebContext
import org.pac4j.core.engine.SecurityGrantedAccessAdapter
import org.pac4j.core.profile.UserProfile
import zio.ZLayer

class ZioSecurityGrantedAccess extends SecurityGrantedAccessAdapter {

  override def adapt(
      context: WebContext,
      sessionStore: org.pac4j.core.context.session.SessionStore,
      profiles: java.util.Collection[UserProfile]
  ): AnyRef = {
    context match {
      case zioContext: ZioWebContext =>
        Some(zioContext)
      case _ =>
        None
    }
  }

}

object ZioSecurityGrantedAccess {
  lazy val live: ZLayer[Any, Nothing, SecurityGrantedAccessAdapter] =
    ZLayer.fromFunction(() => new ZioSecurityGrantedAccess())
}
