package me.seroperson.zio.http.pac4j

import zio._
import zio.http._
import org.pac4j.core.profile.UserProfile
import org.pac4j.core.client

case class SecurityConfig(
    clients: List[client.Client]
)
