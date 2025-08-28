package me.seroperson.zio.http.pac4j

import me.seroperson.zio.http.pac4j.ZioPac4jDefaults
import me.seroperson.zio.http.pac4j.adapter.ZioWebContext
import me.seroperson.zio.http.pac4j.config.CallbackConfig
import me.seroperson.zio.http.pac4j.config.LoginConfig
import me.seroperson.zio.http.pac4j.config.LogoutConfig
import me.seroperson.zio.http.pac4j.config.SecurityConfig
import me.seroperson.zio.http.pac4j.config.SessionCookieConfig
import me.seroperson.zio.http.pac4j.session.InMemorySessionRepository
import me.seroperson.zio.http.pac4j.session.SessionRepository
import me.seroperson.zio.http.pac4j.session.ZioSessionStore
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.util.Pac4jConstants
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import zio._
import zio.http._
import zio.http.Header.SetCookie
import zio.test._
import zio.test.Assertion._

object GenericSessionStoreSpec extends ZIOSpecDefault {

  private val defaultLayers = ZLayer.succeed {
    SecurityConfig(clients = List())
  } >+> InMemorySessionRepository.live >+> ZioPac4jDefaults.live

  override def spec = suite("GenericSessionStoreSpec")(
    test("getSessionId retrieves SessionId from requestAttribute") {
      val expectedSessionId = "sessionId"
      (for {
        sessionRepository <- ZIO.service[SessionRepository]
        sessionStore <- ZIO.service[SessionStore]
        webContext = {
          val context = new ZioWebContext(Request())
          context.setRequestAttribute(
            Pac4jConstants.SESSION_ID,
            expectedSessionId
          )
          context
        }
        sessionId = sessionStore
          .getSessionId(webContext, false)
          .toScala
      } yield assert(sessionId)(equalTo(Some(expectedSessionId))))
        .provide(defaultLayers)
    },
    test("getSessionId retrieves SessionId from cookie") {
      val expectedSessionId = "sessionId"
      (for {
        sessionRepository <- ZIO.service[SessionRepository]
        sessionStore <- ZIO.service[SessionStore]
        webContext = {
          val context = new ZioWebContext(
            Request().addCookie(
              Cookie.Request(Pac4jConstants.SESSION_ID, expectedSessionId)
            )
          )
          context
        }
        sessionId = sessionStore
          .getSessionId(webContext, false)
          .toScala
      } yield assert(sessionId)(equalTo(Some(expectedSessionId))))
        .provide(defaultLayers)
    },
    test("getSessionId does not create new session if not asked for it") {
      val expectedSessionId = "sessionId"
      (for {
        sessionRepository <- ZIO.service[SessionRepository]
        sessionStore <- ZIO.service[SessionStore]
        webContext = {
          val context = new ZioWebContext(
            Request()
          )
          context
        }
        sessionId = sessionStore
          .getSessionId(webContext, false)
          .toScala
      } yield assert(sessionId)(equalTo(None)))
        .provide(defaultLayers)
    },
    test("getSessionId creates new session and sets according cookie") {
      val expectedSessionId = "sessionId"
      (
        for {
          sessionRepository <- ZIO.service[SessionRepository]
          sessionStore <- ZIO.service[SessionStore]
          webContext = {
            val context = new ZioWebContext(
              Request()
            )
            context
          }
          sessionIdFromStore = sessionStore
            .getSessionId(webContext, true)
            .toScala
          sessionIdKey = Pac4jConstants.SESSION_ID
          sessionIdFromAttribute = webContext
            .getRequestAttribute(sessionIdKey)
            .toScala
          sessionIdFromCookie = TestUtils.retrieveSessionId(
            webContext.getResponse
          )
        } yield (assert(sessionIdFromStore.isDefined)(equalTo(true)) &&
          assert(sessionIdFromAttribute)(equalTo(sessionIdFromStore)) &&
          assert(sessionIdFromCookie)(equalTo(sessionIdFromStore)))
      )
        .provide(defaultLayers)
    },
    test("get from underlying SessionRepository") {
      val sessionId = "sessionId"
      (for {
        sessionRepository <- ZIO.service[SessionRepository]
        sessionStore <- ZIO.service[SessionStore]
        webContext = {
          val context = new ZioWebContext(
            Request().addCookie(
              Cookie.Request(Pac4jConstants.SESSION_ID, sessionId)
            )
          )
          context
        }
        _ <- sessionRepository.set(sessionId, "key", "value")
        value = sessionStore.get(webContext, "key").toScala
      } yield assert(value)(equalTo(Some("value"))))
        .provide(defaultLayers)
    },
    test("return None on get for unknown SessionId") {
      val sessionId = "sessionId"
      (for {
        sessionRepository <- ZIO.service[SessionRepository]
        sessionStore <- ZIO.service[SessionStore]
        webContext = {
          val context = new ZioWebContext(
            Request()
          )
          context
        }
        value = sessionStore.get(webContext, "key").toScala
      } yield assert(value)(equalTo(None)))
        .provide(defaultLayers)
    },
    test("set to underlying SessionRepository") {
      val sessionId = "sessionId"
      (for {
        sessionRepository <- ZIO.service[SessionRepository]
        sessionStore <- ZIO.service[SessionStore]
        webContext = {
          val context = new ZioWebContext(
            Request().addCookie(
              Cookie.Request(Pac4jConstants.SESSION_ID, sessionId)
            )
          )
          context
        }
        _ = sessionStore.set(webContext, "key", "value")
        session <- sessionRepository.get(sessionId)
        value = session.getOrElse(Map.empty).get("key")
      } yield assert(value)(equalTo(Some("value"))))
        .provide(defaultLayers)
    },
    test("set `null` removes underlying from SessionRepository") {
      val sessionId = "sessionId"
      (for {
        sessionRepository <- ZIO.service[SessionRepository]
        sessionStore <- ZIO.service[SessionStore]
        webContext = {
          val context = new ZioWebContext(
            Request().addCookie(
              Cookie.Request(Pac4jConstants.SESSION_ID, sessionId)
            )
          )
          context
        }
        _ <- sessionRepository.set(sessionId, "key", "value")
        _ = sessionStore.set(webContext, "key", null)
        session <- sessionRepository.get(sessionId)
        value = session.getOrElse(Map.empty).get("key")
      } yield assert(value)(equalTo(None)))
        .provide(defaultLayers)
    },
    test("destroySession removes session from SessionRepository and Context") {
      val sessionId = "sessionId"
      (for {
        sessionRepository <- ZIO.service[SessionRepository]
        sessionStore <- ZIO.service[SessionStore]
        webContext = {
          val context = new ZioWebContext(
            Request()
          )
          context.setRequestAttribute(Pac4jConstants.SESSION_ID, sessionId)
          context
        }
        _ <- sessionRepository.set(sessionId, "key", "should vanish")
        _ = sessionStore.destroySession(webContext)
        session <- sessionRepository.get(sessionId)
        sessionIdAttribute = webContext
          .getRequestAttribute(Pac4jConstants.SESSION_ID)
          .toScala
        sessionIdRequestCookie = webContext
          .getRequestCookies()
          .asScala
          .find(_.getName() == Pac4jConstants.SESSION_ID)
        sessionIdResponseCookie <- TestUtils.retrieveSessionIdZIO(
          webContext.getResponse
        )
      } yield assert(session)(equalTo(None)) &&
        assert(sessionIdAttribute)(equalTo(None)) &&
        assert(sessionIdResponseCookie)(equalTo("")) &&
        assert(sessionIdRequestCookie)(equalTo(None)))
        .provide(defaultLayers)
    },
    test("renewSession moves data from old session to new one") {
      val oldSessionId = "oldSessionId"
      (for {
        sessionRepository <- ZIO.service[SessionRepository]
        sessionStore <- ZIO.service[SessionStore]
        webContext = {
          val context = new ZioWebContext(Request())
          context.setRequestAttribute(Pac4jConstants.SESSION_ID, oldSessionId)
          context
        }
        _ <- sessionRepository.set(oldSessionId, "key", "value")
        _ = sessionStore.renewSession(webContext)
        oldSession <- sessionRepository.get(oldSessionId)
        newSessionId = webContext
          .getRequestAttribute(Pac4jConstants.SESSION_ID)
          .toScala
          .get
          .asInstanceOf[String]
        newSession <- sessionRepository.get(newSessionId)
        newValue = newSession.flatMap(s => s.get("key"))
      } yield assert(oldSession)(equalTo(None)) &&
        assert(newSessionId)(not(equalTo(oldSessionId))) &&
        assert(newValue)(equalTo(Some("value"))))
        .provide(defaultLayers)
    }
  )
}
