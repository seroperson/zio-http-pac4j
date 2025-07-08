package me.seroperson.zio.http.pac4j

import zio._
import zio.http._
import org.pac4j.core.context.WebContext
import org.pac4j.core.context.session.{SessionStore => Pac4jSessionStore}
import java.util.{Map => JMap, Collection => JCollection, Optional}
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.collection.StringOps._

/** Internal data structure to accumulate response data.
  */
case class ResponseData(
    status: Status = Status.Ok,
    headers: Headers = Headers.empty,
    body: Body = Body.empty
)

/** ZIO HTTP adapter for pac4j WebContext. This bridges pac4j's Java-based
  * WebContext API with ZIO HTTP's functional API.
  */
class ZioHttpWebContext(
    val request: Request,
    private val sessionStore: SessionStore,
    private var responseData: ResponseData = ResponseData()
) extends WebContext {

  // Request methods
  override def getRequestParameter(name: String): Optional[String] =
    request.url.queryParams.queryParam(name).toJava

  override def getRequestParameters(): JMap[String, Array[String]] = {
    val paramsMap: Map[String, Array[String]] =
      request.url.queryParams.map.map {
        case (key: String, values: Chunk[String]) => key -> values.toArray
      }
    paramsMap.asJava
  }

  override def getRequestAttribute(name: String): Optional[AnyRef] =
    Optional.empty() // ZIO HTTP doesn't have request attributes by default

  override def setRequestAttribute(name: String, value: AnyRef): Unit = {
    // ZIO HTTP doesn't support mutable request attributes
    // This could be implemented using a mutable map if needed
  }

  override def getRequestHeader(name: String): Optional[String] =
    request.headers.get(name).toJava

  override def getRequestMethod(): String = request.method.name

  override def getRemoteAddr(): String =
    request.headers
      .get("X-Forwarded-For")
      .orElse(request.headers.get("X-Real-IP"))
      .getOrElse("unknown")

  override def getServerName(): String =
    request.headers.get("Host").map(_.split(":")(0)).getOrElse("localhost")

  override def getServerPort(): Int =
    request.headers
      .get("Host")
      .flatMap(_.split(":").lift(1))
      .flatMap(_.toIntOption)
      .getOrElse(
        if (request.url.scheme.fold("http")(_.toString) == "https") 443 else 80
      )

  override def getScheme(): String =
    request.url.scheme.fold("http")(_.toString)

  override def isSecure(): Boolean =
    request.url.scheme.exists(_.toString == "https")

  override def getFullRequestURL(): String = request.url.encode

  override def getRequestCookies()
      : JCollection[org.pac4j.core.context.Cookie] = {
    request.headers
      .get("Cookie")
      .map(parseCookies)
      .getOrElse(List.empty)
      .asJava
  }

  // Response methods
  override def setResponseHeader(name: String, value: String): Unit = {
    responseData = responseData.copy(
      headers = responseData.headers.addHeader(name, value)
    )
  }

  override def getResponseHeader(name: String): Optional[String] = {
    responseData.headers.get(name).toJava
  }

  override def setResponseContentType(contentType: String): Unit = {
    setResponseHeader("Content-Type", contentType)
  }

  override def addResponseCookie(
      cookie: org.pac4j.core.context.Cookie
  ): Unit = {
    val cookieHeader = formatCookie(cookie)
    responseData = responseData.copy(
      headers = responseData.headers.addHeader("Set-Cookie", cookieHeader)
    )
  }

  // Path and URL methods
  override def getPath(): String = request.url.path.encode

  // ZIO HTTP specific methods (not part of pac4j interface)
  def setResponseStatus(code: Int): Unit = {
    responseData = responseData.copy(status = Status.fromInt(code))
  }

  def writeResponseContent(content: String): Unit = {
    responseData = responseData.copy(
      body = Body.fromString(content)
    )
  }

  // Get the built response
  def getResponse: Response =
    Response(
      status = responseData.status,
      headers = responseData.headers,
      body = responseData.body
    )

  // Utility methods
  private def parseCookies(
      cookieHeader: String
  ): List[org.pac4j.core.context.Cookie] = {
    cookieHeader.split(";").map(_.trim).toList.flatMap { cookieStr =>
      cookieStr.split("=", 2) match {
        case Array(name, value) =>
          val cookie = new org.pac4j.core.context.Cookie(name.trim, value.trim)
          Some(cookie)
        case _ => None
      }
    }
  }

  private def formatCookie(cookie: org.pac4j.core.context.Cookie): String = {
    val sb = new StringBuilder()
    sb.append(s"${cookie.getName}=${cookie.getValue}")

    if (cookie.getDomain != null) sb.append(s"; Domain=${cookie.getDomain}")
    if (cookie.getPath != null) sb.append(s"; Path=${cookie.getPath}")
    if (cookie.getMaxAge > -1) sb.append(s"; Max-Age=${cookie.getMaxAge}")
    if (cookie.isSecure) sb.append("; Secure")
    if (cookie.isHttpOnly) sb.append("; HttpOnly")
    if (cookie.getSameSitePolicy != null)
      sb.append(s"; SameSite=${cookie.getSameSitePolicy}")

    sb.toString
  }
}

/** Factory for creating ZIO HTTP WebContext instances.
  */
object ZioHttpWebContext {

  def apply(request: Request, sessionStore: SessionStore): ZioHttpWebContext =
    new ZioHttpWebContext(request, sessionStore)

  def createSessionStore(sessionStore: SessionStore): ZioHttpSessionStore =
    new ZioHttpSessionStore(sessionStore)
}
