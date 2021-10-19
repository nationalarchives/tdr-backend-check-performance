import Dependencies._

ThisBuild / scalaVersion     := "2.13.6"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "uk/gov/nationalarchives/files"
ThisBuild / resolvers +="TDR Releases" at "s3://tdr-releases-mgmt"

lazy val root = (project in file("."))
  .settings(
    name := "performance-checks",
    Universal / packageName := "performance",
    libraryDependencies ++= Seq(
      keycloakCore,
      keycloakAdmin,
      generatedGraphql,
      graphqlClient,
      typesafeConfig,
      catsEffect,
      lambda,
      logs,
      loadBalancing,
      rds,
      s3,
      ecr,
      ecs,
      ec2,
      sso,
      sts,
      ssm,
      sttp,
      sttpCirce,
      sqlite,
      doobie,
      decline,
      declineEffect,
      catsRetry,
      scalaTags,
      cormorant,
      cormorantGeneric
    ).map(_.exclude("org.slf4j", "*")),
    libraryDependencies ++= Seq(
      logback
    )
  ).enablePlugins(JavaAppPackaging, UniversalPlugin)
