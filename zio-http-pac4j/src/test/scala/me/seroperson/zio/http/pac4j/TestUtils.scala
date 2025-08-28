package me.seroperson.zio.http.pac4j

import org.pac4j.core.util.Pac4jConstants
import zio.NonEmptyChunk
import zio.ZIO
import zio.http.Cookie
import zio.http.Header
import zio.http.Headers
import zio.http.Response

object TestUtils {

  def collectSessionCookies(response: Response) =
    response.headers.collect { case Header.SetCookie(value) =>
      value.toRequest
    } match {
      case Nil =>
        Headers()
      case head :: tail =>
        Headers(Header.Cookie(NonEmptyChunk.fromIterable(head, tail)))
    }

  def retrieveSessionIdZIO(response: Response) =
    ZIO.fromOption(retrieveSessionId(response))

  def retrieveSessionId(response: Response) =
    response.headers.collect {
      case Header.SetCookie(
            Cookie.Response(
              Pac4jConstants.SESSION_ID,
              content,
              _,
              _,
              _,
              _,
              _,
              _
            )
          ) =>
        content
    }.headOption

}
