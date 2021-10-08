import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.8"
  lazy val keycloakCore = "org.keycloak" % "keycloak-core" % "11.0.3"
  lazy val keycloakAdmin = "org.keycloak" % "keycloak-admin-client" % "11.0.3"
  lazy val graphqlClient =  "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.15"
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.147"
  lazy val typesafeConfig = "com.typesafe" % "config" % "1.4.1"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.2.9"
  lazy val s3 = "software.amazon.awssdk" % "s3" % "2.17.55"
  lazy val sso = "software.amazon.awssdk" % "sso" % "2.17.55"
}
