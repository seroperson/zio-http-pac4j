package me.seroperson.zio.http.pac4j.session

import zio.Task
import java.util.UUID

trait SessionRepository {

  def get(id: String): Task[Option[Map[String, AnyRef]]]
  def set(id: String, key: String, value: AnyRef): Task[Unit]
  def remove(id: String, key: String): Task[Unit]
  def update(id: String, session: Map[String, AnyRef]): Task[Unit]
  def deleteSession(id: String): Task[Boolean]

  def generateRandomUuid(): Task[UUID]

}
