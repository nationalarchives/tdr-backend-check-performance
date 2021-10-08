import Dependencies._

ThisBuild / scalaVersion     := "2.13.6"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "uk/gov/nationalarchives/files"
ThisBuild / resolvers +="TDR Releases" at "s3://tdr-releases-mgmt"

lazy val root = (project in file("."))
  .settings(
    name := "create-files",
    libraryDependencies ++= Seq(
      keycloakCore,
      keycloakAdmin,
      generatedGraphql,
      graphqlClient,
      typesafeConfig,
      catsEffect,
      s3,
      sso
    )
  )
