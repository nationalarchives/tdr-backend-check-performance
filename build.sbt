import Dependencies._
import sbtrelease.ReleaseStateTransformations._

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.sys.process._

ThisBuild / scalaVersion     := "2.13.6"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "uk/gov/nationalarchives/files"
ThisBuild / resolvers +="TDR Releases" at "s3://tdr-releases-mgmt"

lazy val generateChangelogFile = taskKey[Unit]("Generates a changelog file from the last version")

generateChangelogFile := {
  val changelog = if(Seq("git", "describe", "--tags", "--abbrev=0").! == 0) {
    val lastTag = "git describe --tags --abbrev=0".!!.replace("\n","")
    s"git log $lastTag..HEAD --oneline".!!
  } else {
    "Initial release"
  }
  val folderName = s"${baseDirectory.value}/notes"
  val fileName = s"${version.value}.markdown"
  val fullPath = s"$folderName/$fileName"
  new File(folderName).mkdirs()
  val file = new File(fullPath)
  if(!file.exists()) {
    new File(fullPath).createNewFile
    Files.write(Paths.get(fullPath), changelog.getBytes(StandardCharsets.UTF_8))
  }
  s"git add $fullPath".!!
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
    ghreleaseRepoOrg := "nationalarchives",
    ghreleaseAssets := Seq(file(s"${(target in Universal).value}/${(packageName in Universal).value}.tgz")),
    releaseProcess := Seq[ReleaseStep](
      inquireVersions,
      setReleaseVersion,
      releaseStepTask(generateChangelogFile),
      commitReleaseVersion,
      tagRelease,
      pushChanges,
      releaseStepTask(packageZipTarball in Universal),
      releaseStepInputTask(githubRelease),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  ).enablePlugins(JavaAppPackaging, UniversalPlugin)
