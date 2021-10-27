package uk.gov.nationalarchives.performancechecks.api

import cats.effect.IO
import cats.implicits._
import graphql.codegen.AddConsignment.{addConsignment => ac}
import graphql.codegen.AddFilesAndMetadata.AddFilesAndMetadata.AddFilesAndMetadata
import graphql.codegen.AddFilesAndMetadata.{AddFilesAndMetadata => afam}
import graphql.codegen.AddTransferAgreement.{AddTransferAgreement => ata}
import graphql.codegen.GetFileCheckProgressSummary.{getFileCheckProgressSummary => fcps}
import graphql.codegen.GetSeries.{getSeries => gs}
import graphql.codegen.StartUpload.{StartUpload => su}
import graphql.codegen.GetConsignmentExport.{getConsignmentForExport => gce}
import graphql.codegen.types._
import uk.gov.nationalarchives.performancechecks.api.GraphqlUtility.{ConsignmentData, MatchIdInfo}
import uk.gov.nationalarchives.performancechecks.checksum.ChecksumGenerator
import uk.gov.nationalarchives.performancechecks.database.Database.FileTypes
import uk.gov.nationalarchives.performancechecks.keycloak.UserCredentials

import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class GraphqlUtility(userCredentials: UserCredentials) {

  def createConsignment(body: String): IO[ac.Data] = {
    val client = new UserApiClient[ac.Data, ac.Variables](userCredentials)
    for {
      result <- getSeries(body)
      series <- IO.fromOption(result)(new Exception("No series found"))
      data <- client.result(ac.document, ac.Variables(AddConsignmentInput(series.getSeries.head.seriesid))).map(_.data)
      consignment <- IO.fromOption(data)(new Exception("Consignment was not created"))
    } yield consignment
  }

  def getSeries(body: String): IO[Option[gs.Data]] = {
    val client = new UserApiClient[gs.Data, gs.Variables](userCredentials)
    client.result(gs.document, gs.Variables(body)).map(_.data)
  }

  def createTransferAgreement(consignmentId: UUID): Unit = {
    val client = new UserApiClient[ata.Data, ata.Variables](userCredentials)
    val input = AddTransferAgreementInput(consignmentId, allPublicRecords = true, allCrownCopyright = true, allEnglish = true, appraisalSelectionSignedOff = true, initialOpenRecords = true, sensitivityReviewSignedOff = true)
    client.result(ata.document, ata.Variables(input))
  }

  def addFilesAndMetadata(consignmentId: UUID, parentFolderName: String, matchIdInfo: List[MatchIdInfo]): IO[List[afam.AddFilesAndMetadata]] = {
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
    client.result(afam.document, afam.Variables(input)).map(_.data.get.addFilesAndMetadata)
  }

  def areFileChecksComplete(consignmentId: UUID): IO[Boolean] = {
    val client = new UserApiClient[fcps.Data, fcps.Variables](userCredentials)
    for {
      result <- client.result(fcps.document, fcps.Variables(consignmentId))
      data <- IO.fromOption(result.data)(new Exception("No data found"))
      consignment <- IO.fromOption(data.getConsignment)(new Exception("Consignment not found"))
    } yield {
      val checks = consignment.fileChecks
      checks.checksumProgress.filesProcessed == consignment.totalFiles &&
        checks.ffidProgress.filesProcessed == consignment.totalFiles &&
        checks.antivirusProgress.filesProcessed == consignment.totalFiles
    }
  }

  def createConsignmentAndFiles(): IO[ConsignmentData] = {
    val filePath = "content"
    for {
      consignment <- createConsignment("MOCK1")
      id <- IO.fromOption(consignment.addConsignment.consignmentid)(new Exception("No consignment ID"))
      _ <- IO(createTransferAgreement(id))
      matchIdInfo <-
        new File(filePath).list()
          .zipWithIndex
          .map(zippedPath => ChecksumGenerator.generate(s"$filePath/${zippedPath._1}", zippedPath._2))
          .toList.sequence
      files <- addFilesAndMetadata(id, filePath.split("/").head, matchIdInfo)
    } yield ConsignmentData(id, matchIdInfo, files)
  }

  def getFileTypes(consignmentId: UUID): IO[List[FileTypes]] = {
    val client = new UserApiClient[gce.Data, gce.Variables](userCredentials)
    for {
      exportResult <- client.result(gce.document, gce.Variables(consignmentId))
      exportData <- IO.fromOption(exportResult.data)(new Exception("No export data found"))
      exportConsignment <- IO.fromOption(exportData.getConsignment)(new Exception("No export data found"))
    } yield exportConsignment.files.map(file =>
      FileTypes(file.fileId, file.ffidMetadata.map(_.matches.map(_.puid.getOrElse("")).mkString(",")).getOrElse(""))
    )
  }
}

object GraphqlUtility {
  case class ConsignmentData(consignmentId: UUID, matchIdInfo: List[MatchIdInfo], files: List[AddFilesAndMetadata])
  case class MatchIdInfo(checksum: String, path: Path, matchId: Int, fileSize: Long)
  def apply(userCredentials: UserCredentials): GraphqlUtility = new GraphqlUtility(userCredentials)
}
