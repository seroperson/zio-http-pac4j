# example/zio-sveltekit

<p align="center">
  <img src="demo.gif" alt="Preview with SvelteKit" width="540px">
</p>

This project contains an example of using `zio-http` + `pac4j` with Google and
GitHub OAuth providers + integration with Svelte frontend. In a nutshell that's
basically the same `zio-form-oauth` example, but with separate frontend instead
of server-side rendered one.

## Running

To start it, cd into `example/zio-sveltekit` and run `docker-compose up --build`
to build and start backend + frontend services. Then you can proceed to
`http://localhost:9000` in your browser to click the buttons.

Just like the `zio-form-oauth` example, this one also requires set of secret
variables to be set, which are already provided by default via `.env` root file.
