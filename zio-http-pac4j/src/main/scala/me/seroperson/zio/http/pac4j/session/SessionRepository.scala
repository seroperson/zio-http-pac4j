package me.seroperson.zio.http.pac4j.session

import java.util.UUID
import zio.Task

/** Repository for managing user session data in the PAC4J ZIO HTTP integration.
  *
  * This trait provides the contract for storing, retrieving, and managing
  * session data across different storage backends (in-memory, Redis, database,
  * etc.). Sessions are identified by unique string IDs and contain key-value
  * pairs of session attributes.
  */
trait SessionRepository {

  /** Retrieves the complete session data for a given session ID.
    *
    * @param id
    *   The unique session identifier
    * @return
    *   A Task that succeeds with Some(session data) if the session exists, or
    *   None if the session doesn't exist or has expired
    */
  def get(id: String): Task[Option[Map[String, AnyRef]]]

  /** Sets a specific key-value pair in the session.
    *
    * @param id
    *   The unique session identifier
    * @param key
    *   The session attribute key
    * @param value
    *   The session attribute value
    * @return
    *   A Task that succeeds when the value has been successfully stored
    */
  def set(id: String, key: String, value: AnyRef): Task[Unit]

  /** Removes a specific key from the session.
    *
    * @param id
    *   The unique session identifier
    * @param key
    *   The session attribute key to remove
    * @return
    *   A Task that succeeds when the key has been successfully removed
    */
  def remove(id: String, key: String): Task[Unit]

  /** Updates the entire session with new data, replacing all existing
    * attributes.
    *
    * @param id
    *   The unique session identifier
    * @param session
    *   The new complete session data
    * @return
    *   A Task that succeeds when the session has been successfully updated
    */
  def update(id: String, session: Map[String, AnyRef]): Task[Unit]

  /** Completely deletes a session and all its associated data.
    *
    * @param id
    *   The unique session identifier
    * @return
    *   A Task that succeeds with true if the session was deleted, or false if
    *   the session didn't exist
    */
  def deleteSession(id: String): Task[Boolean]

  /** Generates a new random UUID for use as a session identifier.
    *
    * @return
    *   A Task that succeeds with a newly generated UUID
    */
  def generateRandomUuid(): Task[UUID]

}
