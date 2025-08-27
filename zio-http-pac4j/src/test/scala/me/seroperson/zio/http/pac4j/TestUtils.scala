package me.seroperson.zio.http.pac4j

import zio.http.Response
import zio.ZIO
import zio.http.Header
import zio.NonEmptyChunk
import zio.http.Cookie
import org.pac4j.core.util.Pac4jConstants

object TestUtils {

  def collectSessionCookies(response: Response) = response.headers.collect {
    case Header.SetCookie(value) =>
      Header.Cookie(NonEmptyChunk(value.toRequest))
  }

  def retrieveSessionId(response: Response) =
    ZIO.fromOption(
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
    )

}
