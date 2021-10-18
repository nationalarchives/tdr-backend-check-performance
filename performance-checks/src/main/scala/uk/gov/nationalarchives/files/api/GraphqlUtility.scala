package uk.gov.nationalarchives.files.api

import cats.effect.IO
import cats.implicits._
import graphql.codegen.AddConsignment.{addConsignment => ac}
import graphql.codegen.AddFilesAndMetadata.AddFilesAndMetadata.AddFilesAndMetadata
import graphql.codegen.AddFilesAndMetadata.{AddFilesAndMetadata => afam}
import graphql.codegen.AddTransferAgreement.{AddTransferAgreement => ata}
import graphql.codegen.GetFileCheckProgressSummary.{getFileCheckProgressSummary => fcps}
import graphql.codegen.GetSeries.{getSeries => gs}
import graphql.codegen.StartUpload.{StartUpload => su}
import graphql.codegen.types._
import uk.gov.nationalarchives.files.api.GraphqlUtility.{ConsignmentData, MatchIdInfo}
import uk.gov.nationalarchives.files.checksum.ChecksumGenerator
import uk.gov.nationalarchives.files.keycloak.UserCredentials

import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class GraphqlUtility(userCredentials: UserCredentials) {

  def createConsignment(body: String): Option[ac.Data] = {
    val client = new UserApiClient[ac.Data, ac.Variables](userCredentials)
    val seriesId: UUID = getSeries(body).get.getSeries.head.seriesid
    client.result(ac.document, ac.Variables(AddConsignmentInput(seriesId))).data
  }

  def getSeries(body: String): Option[gs.Data] = {
    val client = new UserApiClient[gs.Data, gs.Variables](userCredentials)
    client.result(gs.document, gs.Variables(body)).data
  }

  def createTransferAgreement(consignmentId: UUID): Unit = {
    val client = new UserApiClient[ata.Data, ata.Variables](userCredentials)
    val input = AddTransferAgreementInput(consignmentId, allPublicRecords = true, allCrownCopyright = true, allEnglish = true, appraisalSelectionSignedOff = true, initialOpenRecords = true, sensitivityReviewSignedOff = true)
    client.result(ata.document, ata.Variables(input))
  }

  def addFilesAndMetadata(consignmentId: UUID, parentFolderName: String, matchIdInfo: List[MatchIdInfo]): List[afam.AddFilesAndMetadata] = {
    val startUploadClient = new UserApiClient[su.Data, su.Variables](userCredentials)
    startUploadClient.result(su.document, su.Variables(StartUploadInput(consignmentId, parentFolderName)))
    val client: UserApiClient[afam.Data, afam.Variables] = new UserApiClient[afam.Data, afam.Variables](userCredentials)

    val metadataInput = matchIdInfo.map(info =>
      ClientSideMetadataInput(
        s"E2E_tests/original/path${info.matchId}",
        info.checksum,
        Instant.now().toEpochMilli,
        1024,
        info.matchId
      )
    )
    val input = AddFileAndMetadataInput(consignmentId, metadataInput)
    client.result(afam.document, afam.Variables(input)).data.get.addFilesAndMetadata
  }

  def areFileChecksComplete(consignmentId: UUID): Boolean = {
    val client = new UserApiClient[fcps.Data, fcps.Variables](userCredentials)
    val consignmentResult = for {
      data <- client.result(fcps.document, fcps.Variables(consignmentId)).data
      consignment <- data.getConsignment
    } yield consignment
    consignmentResult.forall(consignment => {
      val checks = consignment.fileChecks
      checks.checksumProgress.filesProcessed == consignment.totalFiles &&
        checks.ffidProgress.filesProcessed == consignment.totalFiles &&
        checks.antivirusProgress.filesProcessed == consignment.totalFiles
    })
  }

  def createConsignmentAndFiles(client: GraphqlUtility, filePath: String): IO[ConsignmentData] = {
    for {
      consignment <- IO.fromOption(client.createConsignment("MOCK1"))(new Exception("No consignment"))
      id <- IO.fromOption(consignment.addConsignment.consignmentid)(new Exception("No consignment ID"))
      _ <- IO(client.createTransferAgreement(id))
      matchIdInfo <-
        new File(filePath).list()
          .zipWithIndex
          .map(zippedPath => ChecksumGenerator.generate(s"$filePath/${zippedPath._1}", zippedPath._2))
          .toList.sequence
      files <- IO(client.addFilesAndMetadata(id, filePath.split("/").head, matchIdInfo))
    } yield ConsignmentData(id, matchIdInfo, files)
  }
}

object GraphqlUtility {
  case class ConsignmentData(consignmentId: UUID, matchIdInfo: List[MatchIdInfo], files: List[AddFilesAndMetadata])
  case class MatchIdInfo(checksum: String, path: Path, matchId: Int)
  def apply(userCredentials: UserCredentials): GraphqlUtility = new GraphqlUtility(userCredentials)
}
