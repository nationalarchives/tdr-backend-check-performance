package uk.gov.nationalarchives.performancechecks.keycloak

import com.typesafe.config.ConfigFactory

import javax.ws.rs.core.Response
import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.resource.{RealmResource, UsersResource}
import org.keycloak.admin.client.{Keycloak, KeycloakBuilder}
import org.keycloak.representations.idm.{CredentialRepresentation, UserRepresentation}
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.performancechecks.aws.SystemsManagerUtils

import scala.jdk.CollectionConverters._
import scala.language.postfixOps

object KeycloakClient {
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

  private val configuration = ConfigFactory.load()
  private val authUrl: String = configuration.getString("tdr.auth.url")
  private val userAdminClient: String = configuration.getString("keycloak.user.admin.client")

  private def keyCloakAdminClient(userAdminSecret: String): Keycloak = KeycloakBuilder.builder()
    .serverUrl(s"$authUrl/auth")
    .realm("tdr")
    .clientId(userAdminClient)
    .clientSecret(userAdminSecret)
    .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
    .build()

  private def realmResource(client: Keycloak): RealmResource = client.realm("tdr")
  private def userResource(realm: RealmResource): UsersResource = realm.users()

  def createUser(userCredentials: UserCredentials): String = {
    val adminSecret = SystemsManagerUtils.sandboxParameter("/sbox/keycloak/user_admin_client/secret")
    val client = keyCloakAdminClient(adminSecret)
    val realm = realmResource(client)
    val user = userResource(realm)

    val userRepresentation: UserRepresentation = new UserRepresentation

    val credentials: CredentialRepresentation = new CredentialRepresentation
    credentials.setTemporary(false)
    credentials.setType(CredentialRepresentation.PASSWORD)
    credentials.setValue(userCredentials.password)

    val creds = List(credentials).asJava

    userRepresentation.setUsername(userCredentials.userName)
    userRepresentation.setFirstName(userCredentials.firstName)
    userRepresentation.setLastName(userCredentials.lastName)
    userRepresentation.setEnabled(true)
    userRepresentation.setCredentials(creds)
    userRepresentation.setGroups(List(s"/transferring_body_user/Mock 1 Department").asJava)
    userRepresentation.setRealmRoles(List("tdr_user").asJava)

    val response: Response = user.create(userRepresentation)

    val path = response.getLocation.getPath.replaceAll(".*/([^/]+)$", "$1")
    client.close()
    path
  }

  def deleteUser(userId: String, adminSecret: String): Unit = {
    val client = keyCloakAdminClient(adminSecret)
    val realm = realmResource(client)
    val user = userResource(realm)

    user.delete(userId)
    client.close()
  }
}

case class UserCredentials(userName: String,
                           password: String,
                           firstName: String = "Performance Test First Name",
                           lastName: String = "Performance Test Last Name")
