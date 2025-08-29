package me.seroperson.zio.http.pac4j.adapter

import org.pac4j.core.context.HttpConstants
import org.pac4j.core.context.WebContext
import org.pac4j.core.credentials.UsernamePasswordCredentials
import org.pac4j.core.credentials.extractor.FormExtractor
import org.pac4j.core.exception.http.BadRequestAction
import org.pac4j.core.exception.http.ForbiddenAction
import org.pac4j.core.exception.http.FoundAction
import org.pac4j.core.exception.http.HttpAction
import org.pac4j.core.exception.http.NoContentAction
import org.pac4j.core.exception.http.OkAction
import org.pac4j.core.exception.http.RedirectionAction
import org.pac4j.core.exception.http.SeeOtherAction
import org.pac4j.core.exception.http.UnauthorizedAction
import org.pac4j.core.http.adapter.HttpActionAdapter
import zio.ZIO
import zio.ZLayer
import zio.http.Header
import zio.http.MediaType
import zio.http.Status

class ZioHttpActionAdapter extends HttpActionAdapter {
  override def adapt(action: HttpAction, context: WebContext): AnyRef = {
    val zioContext = context.asInstanceOf[ZioWebContext]
    action match {
      case x: FoundAction =>
        zioContext.setResponseStatus(Status.Found)
        zioContext.setResponseHeader(Header.Location.name, x.getLocation)
      case x: SeeOtherAction =>
        zioContext.setResponseStatus(Status.Found)
        zioContext.setResponseHeader(Header.Location.name, x.getLocation)
      case x: OkAction =>
        zioContext.setContent(x.getContent)
        zioContext.setContentType(MediaType.text.html)
        zioContext.setResponseStatus(Status.Ok)
      case x: BadRequestAction =>
        zioContext.setResponseStatus(Status.BadRequest)
      case x: ForbiddenAction =>
        zioContext.setResponseStatus(Status.Forbidden)
      case x: NoContentAction =>
        zioContext.setResponseStatus(Status.NoContent)
      case x: UnauthorizedAction =>
        zioContext.setResponseStatus(Status.Unauthorized)
      case x =>
        zioContext.setResponseStatus(Status.fromInt(x.getCode))
    }
    zioContext.getResponse
  }

}

object ZioHttpActionAdapter {
  lazy val live: ZLayer[Any, Nothing, HttpActionAdapter] =
    ZLayer.fromFunction(() => new ZioHttpActionAdapter())
}
