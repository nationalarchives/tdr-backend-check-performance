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
    if(createResources) {
      for {
        _ <- updateLambdas(lambdas)
        _ <- retry(invokeLambda, "create-db-users")
        _ <- IO.sleep(1.minutes) //Give the database users time to create
        _ <- retry(invokeLambda,"create-keycloak-db-user")
        _ <- retry(invokeLambda, "database-migrations")
        _ <- runFileFormatTask()
        _ <- waitForServices(List("consignmentapi", "keycloak"))
      } yield ()
    } else {
      IO.unit
    }
  }

  def createFileCheckResults(files: List[Path], createResults: Boolean): IO[Unit] = {
    if(createResults) {
      {
        val userName: String = randomString
        val password: String = randomString
        val userCredentials: UserCredentials = UserCredentials(userName, password)
        KeycloakClient.createUser(userCredentials)
        val graphqlClient: GraphqlUtility = GraphqlUtility(userCredentials)

        def checkFileProgress(consignmentId: UUID): IO[Unit] = {
          for {
            checksComplete <- graphqlClient.areFileChecksComplete(consignmentId)
            _ <- if(checksComplete) {
              IO.sleep(30.seconds) >> IO.unit
            } else {
              IO.sleep(5.seconds) >> checkFileProgress(consignmentId)
            }
          } yield ()
        }

        val checkNames = List("download-files", "checksum", "yara-av", "file-format", "api-update")
        val database = Database(checkNames)

        for {
          _ <- database.createTables()
          _ <- LogUtils.deleteExistingLogStreams(checkNames)
          _ <- S3Utils.downloadFiles(files)
          consignment <- graphqlClient.createConsignmentAndFiles()
          _ <- database.insertFiles(consignment)
          _ <- S3Utils.uploadConsignmentFiles(UUID.randomUUID(), consignment)
          _ <- checkFileProgress(consignment.consignmentId)
          fileCheckResults <- LogUtils.getResults(checkNames)
          fileTypes <- graphqlClient.getFileTypes(consignment.consignmentId)
          _ <- database.updateFileFormat(fileTypes)
          _ <- database.insertResults(fileCheckResults)
          aggregateResults <- database.getAggregateResults
          reportGenerator = HtmlReport(aggregateResults)
          _ <- reportGenerator.createReport()
          _ <- CsvReport.csvReport(aggregateResults)
        } yield ()
      }
    } else {
      IO.unit
    }
  }

  def destroyResources(destroy: Boolean): IO[Unit] = {
    if(destroy) {
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
