package uk.gov.nationalarchives.performancechecks

import cats.effect.{ExitCode, IO}
import cats.implicits._
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import uk.gov.nationalarchives.performancechecks.api.GraphqlUtility
import uk.gov.nationalarchives.performancechecks.arguments.Args.{PerformanceChecks, performanceChecks}
import uk.gov.nationalarchives.performancechecks.aws.ECSUtils._
import uk.gov.nationalarchives.performancechecks.aws.LambdaUtils._
import uk.gov.nationalarchives.performancechecks.aws.LoadBalancerUtils._
import uk.gov.nationalarchives.performancechecks.aws.{LoadBalancerUtils, LogUtils, RdsUtils, S3Utils}
import uk.gov.nationalarchives.performancechecks.database.Database
import uk.gov.nationalarchives.performancechecks.html.HtmlReport
import uk.gov.nationalarchives.performancechecks.csv.CsvReport
import uk.gov.nationalarchives.performancechecks.keycloak.{KeycloakClient, UserCredentials}
import uk.gov.nationalarchives.performancechecks.retry.Retry.retry

import java.nio.file.Path
import java.util.UUID
import scala.concurrent.duration._
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
    if (createResources) {
      for {
        _ <- updateLambdas(lambdas)
        _ <- IO.println("Finished updating lambdas")
        _ <- retry(invokeLambda, "create-db-users")
        _ <- IO.sleep(1.minutes) //Give the database users time to create
        _ <- retry(invokeLambda, "create-keycloak-db-user")
        _ <- retry(invokeLambda, "database-migrations")
        _ <- IO.println("Finished invoking lambdas")
        _ <- runFileFormatTask()
        _ <- IO.println("Finished running file format task")
        _ <- waitForServices(List("consignmentapi", "keycloak"))
        _ <- IO.println("Finished waiting for the services to start")
      } yield ()
    } else {
      IO.unit
    }
  }

  def createFileCheckResults(files: List[Path], createResults: Boolean): IO[Unit] = {
    if (createResults) {

      def createUser(): IO[UserCredentials] = IO {
        val userName: String = randomString
        val password: String = randomString
        val userCredentials: UserCredentials = UserCredentials(userName, password)
        KeycloakClient.createUser(userCredentials)
        userCredentials
      }

      def checkFileProgress(consignmentId: UUID, graphqlUtility: GraphqlUtility): IO[Unit] = {
        for {
          checksComplete <- graphqlUtility.areFileChecksComplete(consignmentId)
          _ <- if (checksComplete) {
            IO.sleep(30.seconds) >> IO.unit
          } else {
            IO.sleep(5.seconds) >> IO.println("Checking file check progress") >> checkFileProgress(consignmentId, graphqlUtility)
          }
        } yield ()
      }

      val checkNames = List("download-files", "checksum", "yara-av", "file-format", "api-update")
      val database = Database(checkNames)

      for {
        _ <- IO.println("Creating database tables")
        _ <- database.createTables()
        _ <- IO.println("Created database tables; Deleting Log Streams")
        _ <- LogUtils.deleteExistingLogStreams(checkNames)
        _ <- IO.println("Deleted log streams; Downloading files")
        _ <- S3Utils.downloadFiles(files)
        _ <- IO.println("Files downloaded; Creating user")
        userCredentials <- createUser()
        _ <- IO.println("User created; Creating consignment")
        graphqlUtility = GraphqlUtility(userCredentials)
        consignment <- graphqlUtility.createConsignmentAndFiles()
        _ <- IO.println("Created consignment; Inserting files")
        _ <- database.insertFiles(consignment)
        _ <- IO.println("Inserted files. Uploading files")
        _ <- S3Utils.uploadConsignmentFiles(UUID.randomUUID(), consignment)
        _ <- IO.println("Uploaded files; Checking progress")
        _ <- checkFileProgress(consignment.consignmentId, graphqlUtility)
        _ <- IO.println("Getting results")
        fileCheckResults <- LogUtils.getResults(checkNames)
        _ <- IO.println("Getting file types")
        fileTypes <- graphqlUtility.getFileTypes(consignment.consignmentId)
        _ <- IO.println("Updating file format")
        _ <- database.updateFileFormat(fileTypes)
        _ <- IO.println("Inserting results")
        _ <- database.insertResults(fileCheckResults)
        _ <- IO.println("Getting aggregate results")
        aggregateResults <- database.getAggregateResults
        _ <- IO.println("Generating report")
        reportGenerator = HtmlReport(aggregateResults)
        _ <- reportGenerator.createReport()
        _ <- IO.println("Generating csv")
        _ <- CsvReport.csvReport(aggregateResults)
        _ <- IO.println("CSV Generated")
      } yield ()

    } else {
      IO.unit
    }
  }

  def destroyResources(destroy: Boolean): IO[Unit] = {
    if (destroy) {
      for {
        _ <- LoadBalancerUtils.removeDeletionProtection(List("consignmentapi", "keycloak"))
        _ <- RdsUtils.removeDeletionProtection()
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
