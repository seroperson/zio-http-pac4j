package me.seroperson.zio.http.pac4j.adapter

import org.pac4j.core.context.HttpConstants
import org.pac4j.core.context.WebContext
import org.pac4j.core.credentials.UsernamePasswordCredentials
import org.pac4j.core.credentials.extractor.FormExtractor
import org.pac4j.core.exception.http.FoundAction
import org.pac4j.core.exception.http.HttpAction
import org.pac4j.core.exception.http.OkAction
import org.pac4j.core.exception.http.SeeOtherAction
import org.pac4j.core.http.adapter.HttpActionAdapter
import zio.ZIO
import zio.ZLayer
import zio.http.MediaType
import zio.http.Status

class ZioHttpActionAdapter extends HttpActionAdapter {
  override def adapt(action: HttpAction, context: WebContext): AnyRef = {
    val hContext = context.asInstanceOf[ZioWebContext]
    action match {
      case fa: FoundAction =>
        hContext.setResponseStatus(Status.Found.code)
        hContext.setResponseHeader("Location", fa.getLocation)
      case sa: SeeOtherAction =>
        hContext.setResponseStatus(Status.Found.code)
        hContext.setResponseHeader("Location", sa.getLocation)
      case a =>
        a.getCode match {
          case HttpConstants.UNAUTHORIZED =>
            hContext.setResponseStatus(Status.Unauthorized.code)
          case HttpConstants.FORBIDDEN =>
            hContext.setResponseStatus(Status.Forbidden.code)
          case HttpConstants.OK =>
            val okAction = a.asInstanceOf[OkAction]
            hContext.setContent(okAction.getContent)
            hContext.setContentType(MediaType.text.html)
            hContext.setResponseStatus(Status.Ok.code)
          case HttpConstants.NO_CONTENT =>
            hContext.setResponseStatus(Status.NoContent.code)
        }
    }
    hContext.getResponse
  }

}

object ZioHttpActionAdapter {
  lazy val live: ZLayer[Any, Nothing, HttpActionAdapter] =
    ZLayer.fromFunction(() => new ZioHttpActionAdapter())
}
