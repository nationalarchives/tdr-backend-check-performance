import sbt._

object Dependencies {
  private val monovoreDeclineVersion = "2.2.0"
  private val awsVersion = "2.17.58"
  private val cormorantVersion = "0.5.0-M1"
  private val keycloakVersion = "18.0.0"
  private val sttpVersion = "3.5.2"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.8"
  lazy val keycloakCore = "org.keycloak" % "keycloak-core" % keycloakVersion
  lazy val keycloakAdmin = "org.keycloak" % "keycloak-admin-client" % keycloakVersion
  lazy val graphqlClient =  "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.29"
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.239"
  lazy val typesafeConfig = "com.typesafe" % "config" % "1.4.2"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.3.11"
  lazy val ecr = "software.amazon.awssdk" % "ecr" % awsVersion
  lazy val ecs = "software.amazon.awssdk" % "ecs" % awsVersion
  lazy val ec2 = "software.amazon.awssdk" % "ec2" % awsVersion
  lazy val s3 = "software.amazon.awssdk" % "s3" % awsVersion
  lazy val sso = "software.amazon.awssdk" % "sso" % awsVersion
  lazy val sts = "software.amazon.awssdk" % "sts" % awsVersion
  lazy val ssm = "software.amazon.awssdk" % "ssm" % awsVersion
  lazy val lambda = "software.amazon.awssdk" % "lambda" % awsVersion
  lazy val loadBalancing = "software.amazon.awssdk" % "elasticloadbalancingv2" % awsVersion
  lazy val rds = "software.amazon.awssdk" % "rds" % awsVersion
  lazy val logs = "software.amazon.awssdk" % "cloudwatchlogs" % awsVersion
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.2.11"
  lazy val sttp = "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % sttpVersion
  lazy val sttpCirce = "com.softwaremill.sttp.client3" %% "circe" % sttpVersion
  lazy val sqlite = "org.xerial" % "sqlite-jdbc" % "3.36.0.3"
  lazy val doobie = "org.tpolecat" %% "doobie-core" % "1.0.0-RC2"
  lazy val decline = "com.monovore" %% "decline" % monovoreDeclineVersion
  lazy val declineEffect = "com.monovore" %% "decline-effect" % monovoreDeclineVersion
  lazy val catsRetry = "com.github.cb372" %% "cats-retry" % "3.1.0"
  lazy val scalaTags = "com.lihaoyi" %% "scalatags" % "0.11.1"
  lazy val cormorant = "io.chrisdavenport" %% "cormorant-core" % cormorantVersion
  lazy val cormorantGeneric = "io.chrisdavenport" %% "cormorant-generic" % cormorantVersion
}
