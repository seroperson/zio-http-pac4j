package me.seroperson.zio.http.pac4j

import zio._
import zio.http._

object Example extends ZIOAppDefault {
  val homeRoute =
    Method.GET / Root -> handler(Response.text("Hello World!"))

  val jsonRoute =
    Method.GET / "json" -> handler(
      Response.json("""{"greetings": "Hello World!"}""")
    )

  val app = Routes(homeRoute, jsonRoute)

  override val run = Server.serve(app).provide(Server.default)
}
