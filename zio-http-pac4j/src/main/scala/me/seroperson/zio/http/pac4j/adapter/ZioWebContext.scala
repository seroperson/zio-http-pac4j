package me.seroperson.zio.http.pac4j.adapter

import java.nio.charset.StandardCharsets
import java.util.{Collection => JCollection}
import java.util.{Map => JMap}
import java.util.Optional
import org.pac4j.core.context.Cookie
import org.pac4j.core.context.WebContext
import org.pac4j.core.context.session.{SessionStore => Pac4jSessionStore}
import org.pac4j.core.profile.CommonProfile
import org.pac4j.core.util.Pac4jConstants
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.collection.StringOps._
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import zio._
import zio.http.Body
import zio.http.Cookie.SameSite
import zio.http.MediaType
import zio.http.MediaTypes
import zio.http.Path
import zio.http.Request
import zio.http.Response
import zio.http.Status

class ZioWebContext(
    private val request: Request
) extends WebContext {

  private val attributes = scala.collection.mutable.Map[String, AnyRef]()
  private var response = Response()

  override def getRequestParameter(name: String): Optional[String] = {
    if (request.hasFormUrlencodedContentType) {
      unsafeRun(request.body.asURLEncodedForm)
        .get(name)
        .flatMap(_.stringValue)
        .orElse(request.queryParameters.queryParam(name))
        .toJava
    } else {
      request.queryParameters.queryParam(name).toJava
    }
  }

  override def getRequestParameters: java.util.Map[String, Array[String]] = {
    if (request.hasFormUrlencodedContentType) {
      unsafeRun(request.body.asURLEncodedForm).map.values
        .flatMap { a =>
          a.stringValue.map(value => (a.name, Array(value)))
        }
        .toMap
        .asJava
    } else {
      request.queryParameters.map.map { case (k, v) =>
        (k, v.toArray)
      }.asJava
    }
  }

  override def getRequestAttribute(name: String): Optional[AnyRef] =
    attributes.get(name).toJava

  override def setRequestAttribute(name: String, value: AnyRef): Unit =
    attributes.put(name, value)

  override def getRequestHeader(name: String): Optional[String] =
    request.headers.get(name).toJava

  override def getRequestMethod: String = request.method.name

  override def getRemoteAddr: String =
    request.remoteAddress.map(_.getHostName).orNull

  override def setResponseHeader(name: String, value: String): Unit =
    modifyResponse(_.addHeader(name, value))

  // TODO Parse the input
  override def setResponseContentType(content: String): Unit =
    MediaType.forContentType(content) match {
      case Some(mediaType) =>
        modifyResponse(_.contentType(MediaType.text.html))
      case None =>
        ()
    }

  override def getServerName: String = "localhost"
  // throw new UnsupportedOperationException()

  override def getServerPort: Int = request.url.portOrDefault.getOrElse(0)

  override def getScheme: String = request.url.scheme.map(_.encode).orNull

  override def isSecure: Boolean =
    request.url.scheme.exists(_.isSecure.exists(identity))

  override def getFullRequestURL: String =
    request.url.encode

  override def getRequestCookies: java.util.Collection[Cookie] =
    request.cookies.map { (c: zio.http.Cookie) =>
      new org.pac4j.core.context.Cookie(c.name, c.content)
    }.asJavaCollection

  override def addResponseCookie(cookie: Cookie): Unit =
    modifyResponse(
      _.addCookie(
        zio.http.Cookie.Response.apply(
          name = cookie.getName,
          content = cookie.getValue,
          domain = Option(cookie.getDomain),
          path = Option(cookie.getPath).map(Path.apply),
          isSecure = cookie.isSecure,
          isHttpOnly = cookie.isHttpOnly,
          maxAge = Some(cookie.getMaxAge).filter(_ != -1).map(_.seconds),
          sameSite =
            Option(cookie.getSameSitePolicy()).map(_.toLowerCase()).map {
              case "strict" => SameSite.Strict
              case "lax"    => SameSite.Lax
              case _        => SameSite.None
            }
        )
      )
    )

  override def getPath: String = request.url.path.encode

  override def getRequestContent: String =
    unsafeRun(request.body.asString)

  override def getProtocol: String = request.url.scheme.get.encode

  override def getResponseHeader(name: String): Optional[String] =
    response.headers.get(name).toJava

  def setResponseStatus(status: Status): Unit =
    modifyResponse(_.copy(status = status))

  def setContentType(contentType: MediaType): Unit =
    modifyResponse(_.contentType(contentType))

  def setContent(content: String): Unit =
    modifyResponse(_.copy(body = Body.fromCharSequence(content)))

  def removeResponseCookie(name: String): Unit =
    modifyResponse(_.addCookie(zio.http.Cookie.clear(name)))

  def getRequest: Request = request

  def getResponse: Response = response

  private def modifyResponse(f: Response => Response): Unit = {
    response = f(response)
  }

  private def unsafeRun[T](task: Task[T]): T =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(task)
        .getOrThrowFiberFailure()
    }

}
