package me.seroperson.zio.http.pac4j.adapter

import org.pac4j.core.engine.CallbackLogic
import org.pac4j.core.engine.DefaultCallbackLogic
import org.pac4j.core.engine.DefaultLogoutLogic
import org.pac4j.core.engine.DefaultSecurityLogic
import org.pac4j.core.engine.LogoutLogic
import org.pac4j.core.engine.SecurityLogic
import zio.ZLayer

object ZioLogic {

  lazy val logoutLogic: ZLayer[Any, Nothing, LogoutLogic] =
    ZLayer.fromFunction(() => new DefaultLogoutLogic())

  lazy val callbackLogic: ZLayer[Any, Nothing, CallbackLogic] =
    ZLayer.fromFunction(() => new DefaultCallbackLogic())

  lazy val securityLogic: ZLayer[Any, Nothing, SecurityLogic] =
    ZLayer.fromFunction(() => new DefaultSecurityLogic())

}
