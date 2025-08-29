import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import java.security.KeyPairGenerator
import java.util.Date
import me.seroperson.zio.http.pac4j._
import me.seroperson.zio.http.pac4j.adapter.ZioHttpActionAdapter
import me.seroperson.zio.http.pac4j.adapter.ZioLogic
import me.seroperson.zio.http.pac4j.adapter.ZioSecurityGrantedAccess
import me.seroperson.zio.http.pac4j.config.CallbackConfig
import me.seroperson.zio.http.pac4j.config.LoginConfig
import me.seroperson.zio.http.pac4j.config.LogoutConfig
import me.seroperson.zio.http.pac4j.config.SecurityConfig
import me.seroperson.zio.http.pac4j.config.SessionCookieConfig
import me.seroperson.zio.http.pac4j.session.InMemorySessionRepository
import me.seroperson.zio.http.pac4j.session.ZioSessionStore
import org.pac4j.core.credentials.authenticator.Authenticator
import org.pac4j.core.profile.CommonProfile
import org.pac4j.core.profile.UserProfile
import org.pac4j.http.client.direct.DirectBasicAuthClient
import org.pac4j.http.client.direct.DirectFormClient
import org.pac4j.http.client.direct.HeaderClient
import org.pac4j.http.client.direct.ParameterClient
import org.pac4j.http.client.indirect.FormClient
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator
import org.pac4j.jwt.config.encryption.ECEncryptionConfiguration
import org.pac4j.jwt.config.encryption.EncryptionConfiguration
import org.pac4j.jwt.config.encryption.SecretEncryptionConfiguration
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration
import org.pac4j.jwt.config.signature.SignatureConfiguration
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator
import org.pac4j.jwt.profile.JwtGenerator
import org.pac4j.oauth.client.GitHubClient
import org.pac4j.oauth.client.Google2Client
import zio._
import zio.http._
import zio.http.template._
import zio.logging.ConsoleLoggerConfig
import zio.logging.LogFilter
import zio.logging.consoleLogger
import zio.logging.slf4j.bridge.Slf4jBridge

object ZioApi extends ZIOAppDefault {

  val baseUrl = sys.env.getOrElse("BASE_URL", "http://localhost:9000")

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >+>
      Slf4jBridge.initialize >+>
      consoleLogger(
        ConsoleLoggerConfig.default.copy(filter =
          LogFilter.LogLevelByNameConfig.default
            .copy(rootLevel = LogLevel.Debug)
        )
      )

  val userRoutes = Routes(
    Method.GET / "jwt" -> Handler.fromFunctionZIO { (req: Request) =>
      for {
        profile <- ZIO.service[UserProfile]
        encConfig <- ZIO.service[EncryptionConfiguration]
        signatureConfig <- ZIO.service[SignatureConfiguration]
        jwtGenerator = {
          val jwtGenerator = new JwtGenerator(
            signatureConfig,
            encConfig
          )
          jwtGenerator
            .setExpirationTime(
              // 1 minute
              new Date(java.lang.System.currentTimeMillis() + 1000L * 60)
            )
          jwtGenerator
        }
        token = jwtGenerator.generate(profile)
      } yield Response.ok.copy(body = Body.fromCharSequence(token))
    } @@ [EncryptionConfiguration with SignatureConfiguration] Pac4jMiddleware
      .securityFilter(clients = List("DirectBasicAuthClient")),
    Method.GET / "protected-query" -> handler {
      for {
        profile <- ZIO.service[UserProfile]
      } yield Response.ok
        .copy(body = Body.fromCharSequence(profile.getUsername))
    } @@ Pac4jMiddleware
      .securityFilter(clients = List("ParameterClient")),
    Method.GET / "protected-header" -> handler {
      for {
        profile <- ZIO.service[UserProfile]
      } yield Response.ok
        .copy(body = Body.fromCharSequence(profile.getUsername))
    } @@ Pac4jMiddleware
      .securityFilter(clients = List("HeaderClient"))
  )

  override val run = for {
    _ <- ZIO.logInfo(s"Starting on $baseUrl")
    _ <- Server
      .serve(userRoutes)
      .provide(
        Server.defaultWithPort(9000),
        ZioPac4jDefaults.live,
        InMemorySessionRepository.live,
        ZLayer.succeed[EncryptionConfiguration] {
          val keyGen = KeyPairGenerator.getInstance("EC")
          val ecKeyPair = keyGen.generateKeyPair()
          val encConfig = new ECEncryptionConfiguration(ecKeyPair)
          encConfig.setAlgorithm(JWEAlgorithm.ECDH_ES_A128KW)
          encConfig.setMethod(EncryptionMethod.A192CBC_HS384)
          encConfig
        },
        ZLayer.succeed[SignatureConfiguration] {
          new SecretSignatureConfiguration(
            // https://www.grc.com/passwords.htm
            "VOAAvi(F2Wi9LiybnxNOJGSryxX58@;v@5Ciz5Cv~WQ|8_yh]ZAIhqDAYhZ3}r{"
          )
        },
        ZLayer.fromZIO {
          for {
            encConfig <- ZIO.service[EncryptionConfiguration]
            signatureConfig <- ZIO.service[SignatureConfiguration]
          } yield SecurityConfig(clients = {
            val jwtAuthenticator = new JwtAuthenticator()
            /*jwtAuthenticator.addEncryptionConfiguration(
              new SecretEncryptionConfiguration(
                "I want one token please, I am a normal user and I am a good guy"
              )
            )*/

            jwtAuthenticator.addEncryptionConfiguration(encConfig)
            jwtAuthenticator.setSignatureConfiguration(signatureConfig)

            List(
              {
                val parameterClient = new ParameterClient(
                  "token",
                  jwtAuthenticator
                )
                parameterClient.setSupportGetRequest(true)
                parameterClient.setSupportPostRequest(false)
                parameterClient
              }, {
                val headerClient = new HeaderClient(
                  Header.Authorization.name,
                  jwtAuthenticator
                )
                headerClient
              }, {
                val directBasicAuthClient =
                  new DirectBasicAuthClient(
                    new SimpleTestUsernamePasswordAuthenticator()
                  )
                directBasicAuthClient
              }
            )
          })
        }
      )
  } yield ()
}
