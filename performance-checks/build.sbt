import Dependencies._
import com.typesafe.sbt.packager.linux.LinuxPlugin.mapGenericFilesToLinux

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
      sso,
      sts,
      ssm,
      sttp,
      sttpCirce,
      sqlite,
      doobie,
      decline,
      declineEffect,
      catsRetry
    ).map(_.exclude("org.slf4j", "*")),
    libraryDependencies ++= Seq(
      logback
    ),
    mapGenericFilesToLinux
  ).enablePlugins(JavaAppPackaging, UniversalPlugin)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")


