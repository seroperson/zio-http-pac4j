package me.seroperson.zio.http.pac4j

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

/** pac4j SessionStore adapter for ZIO HTTP.
  */
class ZioHttpSessionStore(sessionStore: SessionStore)
    extends Pac4jSessionStore {

  override def getSessionId(
      context: WebContext,
      createSession: Boolean
  ): Optional[String] = {
    // Try to get existing session ID from cookie
    val existingSessionId = context
      .getRequestCookies()
      .asScala
      .find(_.getName == "SESSIONID")
      .map(_.getValue)

    existingSessionId match {
      case Some(sessionId)       => Optional.of(sessionId)
      case None if createSession =>
        // Generate new session ID
        val runtime = Runtime.default
        val sessionId = Unsafe.unsafe { implicit unsafe =>
          runtime.unsafe
            .run(sessionStore.generateSessionId())
            .getOrThrowFiberFailure()
        }

        // Set session cookie
        val cookie = new org.pac4j.core.context.Cookie("SESSIONID", sessionId)
        cookie.setHttpOnly(true)
        cookie.setSecure(context.isSecure())
        cookie.setPath("/")
        context.addResponseCookie(cookie)

        Optional.of(sessionId)
      case None => Optional.empty()
    }
  }

  override def get(context: WebContext, key: String): Optional[AnyRef] = {
    this
      .getSessionId(context, false)
      .toScala
      .flatMap { sessionId =>
        val runtime = Runtime.default
        val sessionData = Unsafe.unsafe { implicit unsafe =>
          runtime.unsafe
            .run(sessionStore.get(sessionId))
            .getOrThrowFiberFailure()
        }
        sessionData.flatMap(_.get(key))
      }
      .map(_.asInstanceOf[AnyRef])
      .toJava
  }

  override def set(context: WebContext, key: String, value: AnyRef): Unit = {
    this.getSessionId(context, true).toScala.foreach { sessionId =>
      val runtime = Runtime.default
      Unsafe.unsafe { implicit unsafe =>
        val currentData = runtime.unsafe
          .run(sessionStore.get(sessionId))
          .getOrThrowFiberFailure()
          .getOrElse(Map.empty)
        val updatedData = currentData + (key -> value)
        runtime.unsafe
          .run(sessionStore.put(sessionId, updatedData))
          .getOrThrowFiberFailure()
      }
    }
  }

  override def destroySession(context: WebContext): Boolean = {
    this.getSessionId(context, false).toScala.exists { sessionId =>
      val runtime = Runtime.default
      Unsafe.unsafe { implicit unsafe =>
        runtime.unsafe
          .run(sessionStore.remove(sessionId))
          .getOrThrowFiberFailure()
      }

      // Clear session cookie
      val cookie = new org.pac4j.core.context.Cookie("SESSIONID", "")
      cookie.setMaxAge(0)
      cookie.setPath("/")
      context.addResponseCookie(cookie)

      true
    }
  }

  override def getTrackableSession(context: WebContext): Optional[AnyRef] = {
    this.getSessionId(context, false).map(_.asInstanceOf[AnyRef])
  }

  override def buildFromTrackableSession(
      context: WebContext,
      trackableSession: AnyRef
  ): Optional[Pac4jSessionStore] = {
    Optional.of(this)
  }

  override def renewSession(context: WebContext): Boolean = {
    destroySession(context)
    this.getSessionId(context, true).isPresent
  }
}

object ZioHttpSessionStoreFactory extends SessionStoreFactory {

  override def newSessionStore(
      parameters: FrameworkParameters
  ): Pac4jSessionStore = {
    new ZioHttpSessionStore(InMemorySessionStore())
  }
}
