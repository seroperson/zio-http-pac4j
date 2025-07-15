package me.seroperson.zio.http.pac4j.session

import zio.Task
import java.util.UUID
import scala.annotation.tailrec
import zio.ZIO
import zio.ZLayer

class InMemorySessionRepository extends SessionRepository {
  import scala.collection.concurrent.{Map => ConcurrentMap}
  private val cache =
    scala.collection.concurrent.TrieMap[String, Map[String, AnyRef]]()

  override def remove(id: String, key: String): Task[Unit] =
    ZIO.succeed(insertOrUpdate(cache)(id)(Map.empty, _ - key))

  override def set(id: String, key: String, value: AnyRef): Task[Unit] =
    ZIO.succeed(
      insertOrUpdate(cache)(id)(Map(key -> value), _ + (key -> value))
    )

  override def deleteSession(id: String): Task[Boolean] =
    ZIO.succeed(cache.remove(id).isDefined)

  override def get(id: String): Task[Option[Map[String, AnyRef]]] =
    ZIO.succeed(cache.get(id))

  override def update(id: String, session: Map[String, AnyRef]): Task[Unit] =
    ZIO.succeed(cache.update(id, session))

  private def insertOrUpdate[K, V](
      map: ConcurrentMap[K, V]
  )(key: K)(insert: V, update: V => V): Unit = {
    @tailrec
    def go(): Unit = {
      map.putIfAbsent(key, insert) match {
        case Some(prev) if map.replace(key, prev, update(prev)) => ()
        case Some(_)                                            => go()
        case None                                               => ()
      }
    }
    go()
  }

  override def generateRandomUuid(): Task[UUID] = ZIO.succeed(UUID.randomUUID())

}

object InMemorySessionRepository {
  lazy val live: ZLayer[Any, Nothing, SessionRepository] =
    ZLayer.fromFunction(() => new InMemorySessionRepository())
}
