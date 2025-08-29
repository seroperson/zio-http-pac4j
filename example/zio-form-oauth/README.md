# example/zio-form-oauth

This project contains an example of using `zio-http` + `pac4j` with Google,
GitHub and a simple login + password pair using a HTML form.

## Running

To start it, run `sbt zio-form-oauth/run` from the repository root. It will boot
a webserver at `localhost:9000`, so you can immediately open it in the browser
and try everything you want.

Environment variables `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`,
`GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` are mandatory to run, but they're
already provided via `.env` file from repository root. You can create your own
by following the given guides: [Manage OAuth Clients][1] (Google), [Creating an
OAuth app][2] (GitHub).

[1]: https://support.google.com/cloud/answer/15549257?hl=en
[2]:
  https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app
