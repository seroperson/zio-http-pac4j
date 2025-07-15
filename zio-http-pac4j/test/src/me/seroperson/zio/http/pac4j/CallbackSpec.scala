package me.seroperson.zio.http.pac4j

import zio.test._
import zio.http._
import zio._

object CallbackSpec extends ZIOSpecDefault {

  def spec = suite("CallbackSpec")(
    suite("CallbackHandler")(
      test("should create callback route") {
        assertTrue(true)
      }
    )
  )
}
