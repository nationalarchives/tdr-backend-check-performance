package uk.gov.nationalarchives.files.keycloak
import cats.effect.IO
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto._
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.circe.asJson
import sttp.client3.{basicRequest, _}

import scala.language.postfixOps

object KeycloakUtility {
  val configuration: Config = ConfigFactory.load

  def bearerAccessToken(requestBody: Map[String, String]): IO[BearerAccessToken] = {
    AsyncHttpClientCatsBackend.resource[IO]().use { backend =>
      val authUrl = configuration.getString("tdr.auth.url")

      val request = basicRequest
        .body(requestBody)
        .post(uri"$authUrl/auth/realms/tdr/protocol/openid-connect/token")
        .response(asJson[AuthResponse])

      for {
        response <- request.send(backend)
        body <- IO.fromEither(response.body)
      } yield new BearerAccessToken(body.access_token)
    }
  }
}

case class AuthResponse(access_token: String)
