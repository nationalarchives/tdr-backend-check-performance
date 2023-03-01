import Dependencies._
import sbtrelease.ReleaseStateTransformations._
import java.io.FileWriter

ThisBuild / scalaVersion     := "2.13.10"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "uk.gov.nationalarchives.performancechecks"
ThisBuild / organizationName := "National Archives"

lazy val setLatestTagOutput = taskKey[Unit]("Sets a GitHub actions output for the latest tag")

setLatestTagOutput := {
  val fileWriter = new FileWriter(sys.env("GITHUB_OUTPUT"), true)
  fileWriter.write(s"latest-tag=${(ThisBuild / version).value}")
  fileWriter.close()
}

lazy val root = (project in file("."))
  .settings(
    name := "tdr-backend-check-performance",
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
    ),
    releaseProcess := Seq[ReleaseStep](
      inquireVersions,
      setReleaseVersion,
      releaseStepTask(setLatestTagOutput),
      commitReleaseVersion,
      tagRelease,
      pushChanges,
      releaseStepTask(Universal / packageZipTarball),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )
  .enablePlugins(JavaAppPackaging, UniversalPlugin)
