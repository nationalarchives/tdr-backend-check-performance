package uk.gov.nationalarchives.files.keycloak
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import com.typesafe.config.ConfigFactory
import io.circe.generic.auto._
import sttp.client3.circe.asJson
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend, basicRequest, _}

import scala.language.postfixOps

object KeycloakUtility {
  val configuration = ConfigFactory.load

  def bearerAccessToken(requestBody: Map[String, String]): BearerAccessToken = {

    implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    val authUrl = configuration.getString("tdr.auth.url")

    val response = basicRequest
      .body(requestBody)
      .post(uri"$authUrl/auth/realms/tdr/protocol/openid-connect/token")
      .response(asJson[AuthResponse])
      .send()

    val authResponse = response.body match {
      case Right(body) =>
        body
      case Left(e) => throw e
    }
    new BearerAccessToken(authResponse.access_token)
  }
}

case class AuthResponse(access_token: String)
