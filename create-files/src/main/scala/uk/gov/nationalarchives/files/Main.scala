package uk.gov.nationalarchives.files

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import uk.gov.nationalarchives.files.aws.S3Upload
import uk.gov.nationalarchives.files.checksum.ChecksumGenerator
import uk.gov.nationalarchives.files.graphql.GraphqlUtility
import uk.gov.nationalarchives.files.keycloak.{KeycloakClient, UserCredentials}

import java.io.File
import java.util.UUID
import scala.util.Random

object Main extends IOApp {
  def randomString: String = Random.alphanumeric.dropWhile(_.isDigit).take(10).mkString

  override def run(args: List[String]): IO[ExitCode] = {
    val filePath = args.head
    val userName: String = randomString
    val password: String = randomString
    val userCredentials: UserCredentials = UserCredentials(userName, password)
    KeycloakClient.createUser(userCredentials)
    val client = GraphqlUtility(userCredentials)
    for {
      consignment <- IO.fromOption(client.createConsignment("MOCK1"))(new Exception("No consignment"))
      id <- IO.fromOption(consignment.addConsignment.consignmentid)(new Exception("No consignment ID"))
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
  }







}

