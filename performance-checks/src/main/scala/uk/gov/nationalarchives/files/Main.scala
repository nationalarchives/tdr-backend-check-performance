package uk.gov.nationalarchives.files

import cats.effect.{ExitCode, IO}
import cats.implicits._
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import uk.gov.nationalarchives.files.api.GraphqlUtility
import uk.gov.nationalarchives.files.arguments.Args.{PerformanceChecks, performanceChecks}
import uk.gov.nationalarchives.files.aws.Credentials.{managementAccountNumber, sandboxAccountNumber}
import uk.gov.nationalarchives.files.aws.LambdaUtils._
import uk.gov.nationalarchives.files.aws.LoadBalancerUtils._
import uk.gov.nationalarchives.files.aws.{LoadBalancerUtils, LogUtils, RdsUtils, S3Upload}
import uk.gov.nationalarchives.files.database.Database
import uk.gov.nationalarchives.files.docker.Docker._
import uk.gov.nationalarchives.files.keycloak.{KeycloakClient, UserCredentials}
import uk.gov.nationalarchives.files.retry.Retry.retry
import uk.gov.nationalarchives.files.terraform.Terraform

import java.nio.file.Path
import java.util.UUID
import scala.annotation.tailrec
import scala.util.Random

object Main extends CommandIOApp("performance-checks", "Carry out backend check performance checks") {
  def setupResources(createResources: Boolean): IO[Unit] = {
    val lambdas = List(
      Lambda("antivirus", "yara-av".some),
      Lambda("api-update"),
      Lambda("create-db-users"),
      Lambda("create-db-users", "create-keycloak-db-user".some),
      Lambda("consignment-api-data", "database-migrations".some),
      Lambda("checksum"),
      Lambda("download-files"),
      Lambda("file-format")
    )
    if(createResources) {
      for {
        _ <- retry(Terraform.apply)
        _ <- updateDockerImages("consignment-api", managementAccountNumber, sandboxAccountNumber)
        _ <- updateDockerImages("auth-server", managementAccountNumber, sandboxAccountNumber)
        _ <- updateDockerImages("file-format-build", managementAccountNumber, sandboxAccountNumber)
        _ <- updateLambdas(lambdas)
        _ <- invokeLambdas(List("create-db-users", "create-keycloak-db-user", "database-migrations"))
        _ <- waitForServices(List("consignmentapi", "keycloak"))
      } yield ()
    } else {
      IO.unit
    }
  }

  def createFileCheckResults(files: List[Path], createResults: Boolean): IO[Unit] = {
    if(createResults) {
      files.map(filePath => {
        val userName: String = randomString
        val password: String = randomString
        val userCredentials: UserCredentials = UserCredentials(userName, password)
        KeycloakClient.createUser(userCredentials)
        val graphqlClient: GraphqlUtility = GraphqlUtility(userCredentials)

        @tailrec
        def checkFileProgress(consignmentId: UUID): Unit = {
          if (graphqlClient.areFileChecksComplete(consignmentId)) {
            ()
          } else {
            Thread.sleep(5000)
            checkFileProgress(consignmentId)
          }
        }

        val checkNames = List("download-files", "checksum", "yara-av", "file-format", "api-update")
        val database = Database(checkNames)
        for {
          _ <- database.createTables()
          _ <- LogUtils.deleteExistingLogStreams(checkNames)
          consignment <- graphqlClient.createConsignmentAndFiles(graphqlClient, filePath.toString)
          _ <- database.insertFiles(consignment)
          _ <- IO(S3Upload.uploadConsignmentFiles(UUID.randomUUID(), consignment))
          _ <- IO(checkFileProgress(consignment.consignmentId))
          fileCheckResults <- LogUtils.getResults(checkNames)
          _ <- database.insertResults(fileCheckResults)
        } yield ()
      }).head
    } else {
      IO.unit
    }
  }

  def destroyResources(destroy: Boolean): IO[Unit] = {
    if(destroy) {
      for {
        _ <- LoadBalancerUtils.removeDeletionProtection(List("consignmentapi", "keycloak"))
        _ <- RdsUtils.removeDeletionProtection()
        _ <- retry(Terraform.destroy)
      } yield ()
    } else {
      IO.unit
    }
  }

  override def main: Opts[IO[ExitCode]] = {
    performanceChecks map {
      case PerformanceChecks(files, create, results, destroy) =>
        for {
          _ <- setupResources(create)
          _ <- createFileCheckResults(files, results)
          _ <- destroyResources(destroy)
        } yield ExitCode.Success
    }
  }

  case class FileCheckResults(fileCheckName: String, results: List[Result])
  case class Result(fileId: UUID, timeTaken: Double)

  def randomString: String = Random.alphanumeric.dropWhile(_.isDigit).take(10).mkString
  case class Lambda(repoName: String, lambdaName: Option[String] = None)
}
