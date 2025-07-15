package me.seroperson.zio.http.pac4j.session

import zio._
import zio.http._
import org.pac4j.core.context.WebContext
import org.pac4j.core.context.session.{SessionStore => Pac4jSessionStore}
import java.util.{Map => JMap, Collection => JCollection, Optional}
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.collection.StringOps._
import org.pac4j.core.context.session.SessionStoreFactory
import org.pac4j.core.context.FrameworkParameters
import org.pac4j.core.util.Pac4jConstants
import me.seroperson.zio.http.pac4j.ZioHttpWebContext
import org.pac4j.core.context.{Cookie, WebContext}
import zio.http.Cookie.SameSite

class ZioSessionStore(
    sessionRepository: SessionRepository,
    runtime: Runtime[Any]
)(
    maxAge: Option[Int] = None,
    domain: Option[String] = None,
    path: Option[String] = Some("/"),
    secure: Boolean = false,
    httpOnly: Boolean = false,
    sameSite: Option[SameSite] = None
) extends Pac4jSessionStore {

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
    val id =
      context.getRequestAttribute(Pac4jConstants.SESSION_ID).toScala match {
        case Some(sessionId) => Some(sessionId.toString)
        case None =>
          context.getRequestCookies.asScala.find(
            _.getName == Pac4jConstants.SESSION_ID
          ) match {
            case Some(cookie) => Option(cookie.getValue)
            case None if createSession =>
              Some(createSessionId(context.asInstanceOf[ZioHttpWebContext]))
            case None => None
          }
      }
    // logger.debug(s"getOrCreateSessionId - $id")
    id.toJava
  }

  private def createSessionId(context: ZioHttpWebContext): String = {
    val id = unsafeRun(sessionRepository.generateRandomUuid()).toString
    context.setRequestAttribute(Pac4jConstants.SESSION_ID, id)

    val cookie = new Cookie(Pac4jConstants.SESSION_ID, id)
    maxAge.foreach(cookie.setMaxAge)
    domain.foreach(cookie.setDomain)
    path.foreach(cookie.setPath)
    cookie.setSecure(secure)
    cookie.setHttpOnly(httpOnly)
    sameSite.foreach(s => cookie.setSameSitePolicy(s.toString())) // todo ?

    context.addResponseCookie(cookie)
    id
  }

  override def get(context: WebContext, key: String): Optional[AnyRef] = {
    val sessionId = getSessionId(context, createSession = false)
    sessionId.flatMap[AnyRef] { sid =>
      val value = unsafeRun(sessionRepository.get(sid))
        .getOrElse(Map.empty)
        .get(key)
        .toJava
      // logger.debug(s"get sessionId: $sessionId key: $key")
      value
    }
  }

  override def set(context: WebContext, key: String, value: AnyRef): Unit = {
    val sessionId = getSessionId(context, createSession = true).get()
    if (value == null) {
      unsafeRun(sessionRepository.remove(sessionId, key))
    } else
      unsafeRun(sessionRepository.set(sessionId, key, value))
  }

  override def destroySession(context: WebContext): Boolean = {
    val sessionId = getSessionId(context, createSession = false).toScala
    val deleted =
      sessionId.exists(id => unsafeRun(sessionRepository.deleteSession(id)))
    if (deleted) {
      context.setRequestAttribute(Pac4jConstants.SESSION_ID, null)
      context
        .asInstanceOf[ZioHttpWebContext]
        .removeResponseCookie(Pac4jConstants.SESSION_ID)
    }
    deleted
  }

  override def getTrackableSession(context: WebContext): Optional[AnyRef] = {
    // logger.debug(s"getTrackableSession")
    getSessionId(context, false).asInstanceOf[Optional[AnyRef]]
  }

  override def buildFromTrackableSession(
      context: WebContext,
      trackableSession: Any
  ): Optional[Pac4jSessionStore] = {
    context.setRequestAttribute(
      Pac4jConstants.SESSION_ID,
      trackableSession.toString
    )
    Optional.of(this)
  }

  override def renewSession(context: WebContext): Boolean = {
    val oldSessionId = getSessionId(context, false)
    val oldData =
      oldSessionId.flatMap(sid => unsafeRun(sessionRepository.get(sid)).toJava)

    destroySession(context)

    val newSessionId = createSessionId(
      context.asInstanceOf[ZioHttpWebContext]
    )
    if (oldData.isPresent) {
      unsafeRun(
        sessionRepository.update(newSessionId, oldData.get)
      )
    }
    // logger.debug(s"Renewed session: $oldSessionId -> $newSessionId")
    true
  }

}
