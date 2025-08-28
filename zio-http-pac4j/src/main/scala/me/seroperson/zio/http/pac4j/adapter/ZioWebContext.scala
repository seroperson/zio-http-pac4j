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
import zio.http.Path
import zio.http.Request
import zio.http.Response
import zio.http.Status

class ZioWebContext(
    private val request: Request
) extends WebContext {

  private val logger: Logger = LoggerFactory.getLogger(getClass());
  private val attributes = scala.collection.mutable.Map[String, AnyRef]()
  private var response = Response()

  override def getRequestParameter(name: String): Optional[String] = {
    if (request.hasFormUrlencodedContentType) {
      logger.debug(
        s"getRequestParameter: Getting from Url Encoded Form name=$name"
      )
      unsafeRun(request.body.asURLEncodedForm)
        .get(name)
        .flatMap(_.stringValue)
        .orElse(request.queryParameters.queryParam(name))
        .toJava
    } else {
      logger.debug(s"getRequestParameter: Getting from query params name=$name")
      request.queryParameters.queryParam(name).toJava
    }
  }

  override def getRequestParameters: java.util.Map[String, Array[String]] = {
    if (request.hasFormUrlencodedContentType) {
      logger.debug("getRequestParameters: Getting from Url Encoded Form")
      unsafeRun(request.body.asURLEncodedForm).map.values
        .flatMap { a =>
          a.stringValue.map(value => (a.name, Array(value)))
        }
        .toMap
        .asJava
    } else {
      logger.debug("getRequestParameters: Getting from query params")
      request.queryParameters.map.map { case (k, v) =>
        (k, v.toArray)
      }.asJava
    }
  }

  override def getRequestAttribute(name: String): Optional[AnyRef] = {
    val value = Optional.ofNullable(attributes.get(name).orNull)
    logger.debug(s"getRequestAttribute: $name, result: $value")
    value
  }

  override def setRequestAttribute(name: String, value: AnyRef): Unit = {
    logger.debug(s"setRequestAttribute: $name $value")
    attributes.put(name, value)
  }

  override def getRequestHeader(name: String): Optional[String] =
    Optional.ofNullable(
      request.headers.get(name).orNull
    )

  override def getRequestMethod: String = request.method.name

  override def getRemoteAddr: String =
    request.remoteAddress.map(_.getHostName).orNull

  override def setResponseHeader(name: String, value: String): Unit = {
    logger.debug(s"setResponseHeader $name = $value")
    modifyResponse { r =>
      r.addHeader(name, value)
    }
  }

  override def setResponseContentType(content: String): Unit = {
    logger.debug("setResponseContentType: " + content)
    // TODO Parse the input
    modifyResponse { r =>
      r.contentType(MediaType.text.html)
      /*r.withContentType(
        `Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`))
      )*/
    }
  }

  override def getServerName: String = "localhost"
  // throw new UnsupportedOperationException()
  // request.body().map(_.toInetAddress.getHostName).orNull

  override def getServerPort: Int = request.url.portOrDefault.getOrElse(0)

  override def getScheme: String = request.url.scheme.map(_.encode).orNull

  override def isSecure: Boolean =
    request.url.scheme.exists(_.isSecure.exists(identity))

  override def getFullRequestURL: String = {
    logger.debug(
      s"getFullRequestURL: ${request.url.encode} / ${request.url} / ${request.path} / ${request}"
    )
    request.url.encode
  }

  override def getRequestCookies: java.util.Collection[Cookie] = {
    logger.debug("getRequestCookies")
    val convertCookie = (c: zio.http.Cookie) =>
      new org.pac4j.core.context.Cookie(c.name, c.content)
    val cookies = request.cookies.map(convertCookie)
    cookies.asJavaCollection
  }

  override def addResponseCookie(cookie: Cookie): Unit = {
    logger.debug(s"addResponseCookie ${cookie}")
    val maxAge =
      Option(cookie.getMaxAge).filter(_ != -1).map(_.toLong.seconds)
    val sameSite = Option(cookie.getSameSitePolicy()).map(_.toLowerCase()).map {
      case "strict" => SameSite.Strict
      case "lax"    => SameSite.Lax
      case _        => SameSite.None
    }

    // - `RequestCookie.extension` has no counterpart in `Cookie`;
    // - `Cookie.getComment` can be passed via `extension`, but it's not worth
    // the trouble.
    val zioCookie = zio.http.Cookie.Response.apply(
      name = cookie.getName,
      content = cookie.getValue,
      domain = Option(cookie.getDomain),
      path = Some(Path(cookie.getPath)),
      isSecure = cookie.isSecure,
      isHttpOnly = cookie.isHttpOnly,
      maxAge = maxAge,
      sameSite = sameSite
    )

    modifyResponse(_.addCookie(zioCookie))
  }

  override def getPath: String = request.url.path.encode

  override def getRequestContent: String =
    unsafeRun(request.body.asString)

  override def getProtocol: String = request.url.scheme.get.encode

  override def getResponseHeader(name: String): Optional[String] =
    Optional.ofNullable(
      response.headers.get(name).orNull
    )

  def setResponseStatus(code: Int): Unit = {
    logger.debug(s"setResponseStatus $code")
    modifyResponse { r =>
      r.copy(status = Status.fromInt(code)) // fallback to OK?
    }
  }

  def setContentType(contentType: MediaType): Unit = {
    // logger.debug(s"setContentType $contentType")
    modifyResponse { r =>
      r.contentType(contentType)
    }
  }

  def setContent(content: String): Unit = {
    // logger.debug(s"setContent $content")
    modifyResponse { r =>
      r.copy(body = Body.fromCharSequence(content))
    }
  }

  def removeResponseCookie(name: String): Unit = {
    logger.debug(s"removeResponseCookie $name")
    modifyResponse(_.addCookie(zio.http.Cookie.clear(name)))
  }

  def getRequest: Request = request

  def getResponse: Response = response

  private def modifyResponse(f: Response => Response): Unit = {
    response = f(response)
  }

  private def unsafeRun[T](task: Task[T]): T = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(task)
        .getOrThrowFiberFailure()
    }
  }

}
