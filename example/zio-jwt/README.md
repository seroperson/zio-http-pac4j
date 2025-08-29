# example/zio-jwt

This project contains an example of using `zio-http` + `pac4j` with Basic Auth
and JWT tokens. Such method is suitable for internal services and/or public
APIs.

## Running

To start it, run `sbt zio-jwt/run` from the repository root. It will boot a
webserver at `localhost:9000`, which exposes the following endpoints:

- `/protected-query` - requires JWT token to be passed via `token` query
  parameter. Returns 401 if unauthenticated, 200 otherwise.
- `/protected-header` - requires JWT token to be passed via `Authorization`
  header. Returns 401 if unauthenticated, 200 otherwise.
- `/jwt` - protected with Basic Auth route, which allows all users in with
  `username == password`. Returns 200 with JWT token if authentication passes,
  401 otherwise.

For example, you can retrieve your JWT token like this:

```
$ > curl -v -u admin:admin "http://localhost:9000/jwt"
* Host localhost:9000 was resolved.
* IPv6: ::1
* IPv4: 127.0.0.1
*   Trying [::1]:9000...
* Connected to localhost (::1) port 9000
* Server auth using Basic with user 'admin'
> GET /jwt HTTP/1.1
> Host: localhost:9000
> Authorization: Basic YWRtaW46YWRtaW4=
> User-Agent: curl/8.8.0
> Accept: */*
>
* Request completely sent off
< HTTP/1.1 200 Ok
< date: Fri, 29 Aug 2025 13:00:44 GMT
< content-length: 764
<
* Connection #0 to host localhost left intact
eyJlcGsiOnsia3R5IjoiRUMiLCJjcnYiOiJQLTM4NCIsIngiOiJkNjRKTmdhcjNQRHRDdW11TmluZ2JCV1ljdVlKMmc1bmNNaWlzMXJKSXJYcTZBLXU4ZkRnQkppazd1T3ZfOUpnIiwieSI6Ii1CWmI2NGFKWXU0TVk5R0ZJSThDM05zWlJicUZ6My1CcEo4M1ZJOTVyZDBPdTZ1OS1zRVN4eWowZ2dabW1UQ3cifSwiY3R5IjoiSldUIiwiZW5jIjoiQTE5MkNCQy1IUzM4NCIsImFsZyI6IkVDREgtRVMrQTEyOEtXIn0.DACVuGINH8JaOJF22woRzeP-m2XAwqayS3xrLjitV_walC9TgCsu-hgOgxA7B5GUAJwyTDavFyA.iDL8zIIDxkG5b8-F4KDPuA.GGaPaOti_S1mMZXsZGow0TU-LbEAfm8hU0qADN-KK8vR15OAHoZNgy2Xu-lbgtBkfv0u_Fjr2rzB0db6wAspkXpMj0qYCUt7WeRmn_yky3RuyfRQ606hnTte9C5SPzIvlj7WWYw3Q0SGEKKSYGpbF77XSR5usgf3VziBvDKGSFV3ir9KJ9sjUCOYal44HdqxIoB_NqkeY1U9unWgfbmzRHizA6XwfdgUSLN44puT7hKmAQ3JVpqWf2QVT7w-XvfUoS4wWTjbu5lMgllrrQzna8Kdyaejo9vxSdpZmzh42zsIZ1fwtz2Lm9VpIkAI0pl1.khx6WHdL5t3iuiomP-JPldo2myd2_SG1
```

Accessing `/protected-header` with `Authorization: $TOKEN` should return your
username:

```
$ - curl -H "Authorization: $TOKEN" "http://localhost:9000/protected-header" -v
* Host localhost:9000 was resolved.
* IPv6: ::1
* IPv4: 127.0.0.1
*   Trying [::1]:9000...
* Connected to localhost (::1) port 9000
> GET /protected-header HTTP/1.1
> Host: localhost:9000
> User-Agent: curl/8.8.0
> Accept: */*
> Authorization: ...
>
* Request completely sent off
< HTTP/1.1 200 Ok
< date: Fri, 29 Aug 2025 13:01:39 GMT
< content-length: 5
<
* Connection #0 to host localhost left intact
admin
```

Be aware that token is valid only for one minute for demonstration purposes.

[1]: https://support.google.com/cloud/answer/15549257?hl=en
[2]:
  https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app
