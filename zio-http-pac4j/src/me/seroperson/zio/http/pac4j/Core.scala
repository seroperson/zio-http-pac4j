package me.seroperson.zio.http.pac4j

import zio._
import zio.http._
import org.pac4j.core.profile.UserProfile
import org.pac4j.core.client

trait SessionStore {
  def get(sessionId: String): Task[Option[Map[String, Any]]]
  def put(sessionId: String, data: Map[String, Any]): Task[Unit]
  def remove(sessionId: String): Task[Unit]
  def generateSessionId(): Task[String]
}

case class InMemorySessionStore() extends SessionStore {
  private val sessions =
    scala.collection.concurrent.TrieMap[String, Map[String, Any]]()

  def get(sessionId: String): Task[Option[Map[String, Any]]] =
    ZIO.succeed(sessions.get(sessionId))

  def put(sessionId: String, data: Map[String, Any]): Task[Unit] =
    ZIO.succeed(sessions.put(sessionId, data)).unit

  def remove(sessionId: String): Task[Unit] =
    ZIO.succeed(sessions.remove(sessionId)).unit

  def generateSessionId(): Task[String] =
    ZIO.succeed(java.util.UUID.randomUUID().toString)
}

object SessionStore {
  lazy val live: ZLayer[Any, Throwable, SessionStore] =
    ZLayer.fromFunction(InMemorySessionStore.apply _)
}

case class SecurityContext(
    profile: Option[UserProfile],
    sessionId: Option[String],
    request: Request
)

case class SecurityConfig(
    clients: List[client.Client]
)

