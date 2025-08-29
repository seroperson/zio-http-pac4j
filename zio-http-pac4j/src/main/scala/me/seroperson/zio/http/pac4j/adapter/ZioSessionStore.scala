package me.seroperson.zio.http.pac4j.session

import java.util.{Collection => JCollection}
import java.util.{Map => JMap}
import java.util.Optional
import me.seroperson.zio.http.pac4j.adapter.ZioWebContext
import me.seroperson.zio.http.pac4j.config.SecurityConfig
import org.pac4j.core.context.Cookie
import org.pac4j.core.context.FrameworkParameters
import org.pac4j.core.context.WebContext
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.context.session.SessionStoreFactory
import org.pac4j.core.util.Pac4jConstants
import scala.collection.StringOps._
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import zio._
import zio.http._
import zio.http.Cookie.SameSite

class ZioSessionStore(
    sessionRepository: SessionRepository,
    config: SecurityConfig
) extends SessionStore {

  private val runtime = Runtime.default

  private def unsafeRun[T](task: Task[T]): T = {
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe
        .run(task)
        .getOrThrowFiberFailure()
    }
  }

  override def getSessionId(
      context: WebContext,
      createSession: Boolean
  ): Optional[String] = {
    (context.getRequestAttribute(Pac4jConstants.SESSION_ID).toScala match {
      case Some(sessionId) => Some(sessionId.toString)
      case None =>
        context.getRequestCookies.asScala.find(
          _.getName == Pac4jConstants.SESSION_ID
        ) match {
          case Some(cookie) => Option(cookie.getValue)
          case None if createSession =>
            Some(createSessionId(context.asInstanceOf[ZioWebContext]))
          case None => None
        }
    }).toJava
  }

  private def createSessionId(context: ZioWebContext): String = {
    val id = unsafeRun(sessionRepository.generateRandomUuid()).toString
    context.setRequestAttribute(Pac4jConstants.SESSION_ID, id)

    val cookie = new Cookie(Pac4jConstants.SESSION_ID, id)
    config.sessionCookie.maxAge.foreach(cookie.setMaxAge)
    config.sessionCookie.domain.foreach(cookie.setDomain)
    config.sessionCookie.path.map(_.encode).foreach(cookie.setPath)
    cookie.setSecure(config.sessionCookie.secure)
    cookie.setHttpOnly(config.sessionCookie.httpOnly)
    config.sessionCookie.sameSite.foreach(s =>
      cookie.setSameSitePolicy(s.toString())
    )

    context.addResponseCookie(cookie)
    id
  }

  override def get(context: WebContext, key: String): Optional[AnyRef] = {
    getSessionId(context, createSession = false)
      .flatMap[AnyRef] { sid =>
        val value = unsafeRun(sessionRepository.get(sid))
          .getOrElse(Map.empty)
          .get(key)
          .toJava
        value
      }
  }

  override def set(context: WebContext, key: String, value: AnyRef): Unit = {
    val sessionId = getSessionId(context, createSession = true).get()
    if (value == null) {
      unsafeRun(sessionRepository.remove(sessionId, key))
    } else {
      unsafeRun(sessionRepository.set(sessionId, key, value))
    }
  }

  override def destroySession(context: WebContext): Boolean = {
    val sessionId = getSessionId(context, createSession = false).toScala
    val deleted =
      sessionId.exists(id => unsafeRun(sessionRepository.deleteSession(id)))
    if (deleted) {
      context.setRequestAttribute(Pac4jConstants.SESSION_ID, null)
      context
        .asInstanceOf[ZioWebContext]
        .removeResponseCookie(Pac4jConstants.SESSION_ID)
    }
    deleted
  }

  override def getTrackableSession(context: WebContext): Optional[AnyRef] = {
    getSessionId(context, false).asInstanceOf[Optional[AnyRef]]
  }

  override def buildFromTrackableSession(
      context: WebContext,
      trackableSession: Any
  ): Optional[SessionStore] = {
    context.setRequestAttribute(
      Pac4jConstants.SESSION_ID,
      trackableSession.toString
    )
    Optional.of(this)
  }

  override def renewSession(context: WebContext): Boolean = {
    val oldSessionId = getSessionId(context, false).toScala
    val oldData =
      oldSessionId.flatMap(sid => unsafeRun(sessionRepository.get(sid)))

    destroySession(context)

    val newSessionId = createSessionId(context.asInstanceOf[ZioWebContext])
    oldData.foreach { data =>
      unsafeRun(
        sessionRepository.update(newSessionId, data)
      )
    }
    true
  }

}

object ZioSessionStore {

  lazy val live
      : ZLayer[SessionRepository & SecurityConfig, Nothing, SessionStore] =
    ZLayer.derive[ZioSessionStore]
}
