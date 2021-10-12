package uk.gov.nationalarchives.files


import cats.effect.{ExitCode, IO}
import cats.implicits.toTraverseOps
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import uk.gov.nationalarchives.files.arguments.Args.{CreateFiles, Event, SaveResults, performanceOpts}
import uk.gov.nationalarchives.files.aws.S3Upload
import uk.gov.nationalarchives.files.checksum.ChecksumGenerator
import uk.gov.nationalarchives.files.graphql.GraphqlUtility
import uk.gov.nationalarchives.files.keycloak.{KeycloakClient, UserCredentials}

import java.io.File
import java.util.UUID
import io.circe.generic.auto._
import io.circe.parser.decode
import scala.util.Random

object Main extends CommandIOApp("performance", "Tools for testing the backend checks") {
  case class TimeTaken(fileId: UUID, timeTaken: Int)

  def saveData(input: String): IO[TimeTaken] = {
    IO.fromEither(decode[TimeTaken](input.dropRight(2).trim))
  }

  def randomString: String = Random.alphanumeric.dropWhile(_.isDigit).take(10).mkString

  override def main: Opts[IO[ExitCode]] = {
    performanceOpts map {
      case CreateFiles(files) =>
        files.map(filePath => {
          val userName: String = randomString
          val password: String = randomString
          val userCredentials: UserCredentials = UserCredentials(userName, password)
          KeycloakClient.createUser(userCredentials)
          val client = GraphqlUtility(userCredentials)
          for {
            consignment <- IO.fromOption(client.createConsignment("MOCK1"))(new Exception("No consignment"))
            id <- IO.fromOption(consignment.addConsignment.consignmentid)(new Exception("No consignment ID"))
            _ = println(id)
            _ <- IO(client.createTransferAgreement(id))
            matchIdInfo <- new File(filePath).list()
              .zipWithIndex
              .map(zippedPath => ChecksumGenerator.generate(s"$filePath/${zippedPath._1}", zippedPath._2))
              .toList.sequence
            files <- IO(client.addFilesAndMetadata(id, filePath.split("/").head, matchIdInfo))
            _ <- IO(S3Upload.upload(UUID.randomUUID(), id, files, matchIdInfo))
          } yield {
            ExitCode.Success
          }
        }).head
      case SaveResults(resultName, message) =>
        for {
          data <- saveData(message)
          _ <- IO.println(s"${data.fileId}, ${data.timeTaken}")
        } yield ExitCode.Success
     }

  }







}

