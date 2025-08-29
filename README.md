# zio-http-pac4j

[![Build Status](https://github.com/seroperson/zio-http-pac4j/actions/workflows/build.yml/badge.svg)](https://github.com/seroperson/zio-http-pac4j/actions/workflows/build.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/me.seroperson/zio-http-pac4j_2.12)](https://mvnrepository.com/artifact/me.seroperson/zio-http-pac4j)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/seroperson/zio-http-pac4j/LICENSE)

<!-- prettier-ignore-start -->
> [!WARNING] 
> This library is in an alpha-quality stage. I haven't used it in
> production yet, however it should work okay. API tends to change.
<!-- prettier-ignore-end -->

This library provides [pac4j][1] integration for [zio-http][2]. It allows you to
easily implement authorization, authentication mechanisms to secure your web
service.

This repository is written using [jj][9] (jujutsu) VCS.

## Installation

In case if you use `sbt`:

```sbt
libraryDependencies += "me.seroperson" %% "zio-http-pac4j" % "0.1.0"
```

In case of `mill`:

```scala
mvn"me.seroperson::zio-http-pac4j::0.1.0"
```

This library depends on `pac4j` version `6.x`, which requires you to run at
least JDK 17.

## How to use

The way how you use it depends on your needs. Firstly, be sure to get known with
[pac4j documentation][3] and their [concepts and components][4].

Let's examine how to implement authentication using Google, GitHub and simple
login + password form. Adding necessary OAuth `pac4j` library is mandatory, as
it isn't included out-of-box:

```scala
val pac4jVersion = "6.2.0"

libraryDependencies ++= Seq(
  "org.pac4j" % "pac4j-oauth" % pac4jVersion
  // this module contains login + password logic
  "org.pac4j" % "pac4j-http" % pac4jVersion
)
```

We need to start with filling `SecurityConfig` object:

```scala
import me.seroperson.zio.http.pac4j.config.SecurityConfig
import org.pac4j.oauth.client.GitHubClient
import org.pac4j.oauth.client.Google2Client
import org.pac4j.http.client.indirect.FormClient

val baseUrl = sys.env.getOrElse("BASE_URL", "http://localhost:9000")

SecurityConfig(
  clients = List(
    {
      new Google2Client(
        sys.env.get("GOOGLE_CLIENT_ID").getOrElse(throw new NoSuchElementException),
        sys.env.get("GOOGLE_CLIENT_SECRET").getOrElse(throw new NoSuchElementException)
      ).setCallbackUrl(s"$baseUrl/api/callback")
    }, {
      new GitHubClient(
        sys.env.get("GITHUB_CLIENT_ID").getOrElse(throw new NoSuchElementException),
        sys.env.get("GITHUB_CLIENT_SECRET").getOrElse(throw new NoSuchElementException)
      ).setCallbackUrl(s"$baseUrl/api/callback")
    }, {
      new FormClient(
        /* loginUrl = */ s"$baseUrl/loginForm",
        new SimpleTestUsernamePasswordAuthenticator()
      ).setCallbackUrl(s"$baseUrl/api/callback")
    }
  )
)
```

You can notice `/api/callback` paths here, we will discuss them later.
`SimpleTestUsernamePasswordAuthenticator` instance here is an `Authenticator`,
which allows in all users with `username == password` and must be used only for
testing purposes. For login + password method we also need to implement the form
itself:

```scala
import zio.http._
import zio.http.template._

val userRoutes = Routes(
  Method.GET / "loginForm" -> handler(
    Response.html(
      html(
        head(
          title("Login using a form")
        ),
        body(
          h1("Login using a form"),
          form(
            actionAttr := s"$baseUrl/api/callback?client_name=FormClient",
            methodAttr := "POST",
            input(
              typeAttr := "text",
              nameAttr := "username",
              valueAttr := ""
            ),
            p(),
            input(
              typeAttr := "password",
              nameAttr := "password",
              valueAttr := ""
            ),
            p(),
            input(
              typeAttr := "submit",
              valueAttr := "Submit"
            )
          )
        )
      )
    )
  )
)
```

Also let's add a root route which will be available only for logged in users:

```scala
import zio._
import zio.http._
import me.seroperson.zio.http.pac4j.Pac4jMiddleware

val userRoutes = Routes(
  // ...
  Method.GET / Root -> Handler.fromFunctionZIO { (req: Request) =>
    for {
      profile <- ZIO.service[UserProfile]
      response <- ZIO.succeed(
        Response.html(
          html(
            head(
              title("User Profile")
            ),
            body(
              h1("User Profile"),
              p(s"Authenticated user: ${profile}"),
              p(
                a(href := "/api/logout", "Logout")
              )
            )
          )
        )
      )
    } yield response
  } @@ Pac4jMiddleware.securityFilter(
    clients = List(),
    authorizers = List("IsFullyAuthenticatedAuthorizer")
  )
)
```

Here we've used `Pac4jMiddleware.securityFilter` middleware which also provides
`pac4j`'s `UserProfile` object and restricts this page from unauthenticated
access using [IsFullyAuthenticatedAuthorizer][5] authorizer. There are [more
bundled authorizers][6].

And now we'll need to add some builtin routes to the final set:

```scala
import me.seroperson.zio.http.pac4j.Pac4jMiddleware

val allRoutes = (
  Pac4jMiddleware.callback() ++
    Pac4jMiddleware.logout() ++
    // `clients` argument must contain all available to use clients
    Pac4jMiddleware.login(clients = List("Google2Client", "GitHubClient", "FormClient")) ++
    userRoutes
)
```

All these routes are highly-customizable via arguments and config object, so you
can dive deeper into source code or documentation to get know them better. For
example, using the `route` argument you can replace the default path of `logout`
or `callback` routes.

Finally, let's provide necessary layers and bundle everything together:

```scala
import zio._
import zio.http._
import me.seroperson.zio.http.pac4j.ZioPac4jDefaults
import me.seroperson.zio.http.pac4j.session.InMemorySessionRepository

object ZioApi extends ZIOAppDefault {

  // val securityConfig = ...
  // val allRoutes = ...

  override val run = for {
    _ <- Server
      .serve(allRoutes)
      .provide(
        Server.defaultWithPort(9000),
        ZioPac4jDefaults.live,
        InMemorySessionRepository.live,
        ZLayer.succeed(securityConfig)
      )
  } yield ()
}
```

Here we've used `ZioPac4jDefaults` layer with many builtin ZIO-adapted
implementations of `pac4j` interfaces, like `SessionStore`, `HttpActionAdapter`
and other. `InMemorySessionRepository` service provides storing sessions data in
memory and intended only for testing purposes. To store them somewhere else,
you'll have to implement `SessionRepository` class by yourself.

Now you can start application using `sbt run` and open
`http://localhost:9000/api/login?provider=FormClient`, which will redirect you
to your form. Enter the same value into `username` and `password` fields and
you'll be logged in. You can make sure it by inspecting your cookies.

Accessing `http://localhost:9000/api/login?provider=GitHubClient` will redirect
you to GitHub (and then back to the root route), Google method works the same
way too.

## Examples

What we saw above is basically the `example/zio-form-oauth` example. There are
also a few more complicated examples, like:

- `example/zio-sveltekit`, which shows how to tie different authentication
  methods with Svelte frontend.
- `example/zio-jwt`, which shows how to use Basic Auth + JWT tokens.

You can navigate to corresponding `README.md` files to dive deeper.

## Credits

Many thanks to [http4s-pac4j][7] and [play-pac4j][8] authors, which code I've
used as a base for this library.

## License

```text
MIT License

Copyright (c) 2025 Daniil Sivak

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

[1]: https://github.com/pac4j/pac4j
[2]: https://github.com/zio/zio-http
[3]: https://www.pac4j.org/docs/index.html
[4]: https://www.pac4j.org/docs/main-concepts-and-components.html
[5]:
  https://github.com/pac4j/pac4j/blob/632413e40d47fe0955abd8c1610c88badc214c4a/pac4j-core/src/main/java/org/pac4j/core/authorization/authorizer/IsRememberedAuthorizer.java
[6]:
  https://github.com/pac4j/pac4j/tree/632413e40d47fe0955abd8c1610c88badc214c4a/pac4j-core/src/main/java/org/pac4j/core/authorization/authorizer
[7]: https://github.com/pac4j/http4s-pac4j
[8]: https://github.com/pac4j/play-pac4j/
[9]: https://github.com/jj-vcs/jj
