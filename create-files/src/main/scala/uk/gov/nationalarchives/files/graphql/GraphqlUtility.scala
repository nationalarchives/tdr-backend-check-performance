package uk.gov.nationalarchives.files.graphql

import java.time.Instant
import java.util.UUID
import graphql.codegen.GetConsignmentSummary.getConsignmentSummary
import graphql.codegen.AddFilesAndMetadata.{AddFilesAndMetadata => afam}
import graphql.codegen.StartUpload.{StartUpload => su}
import graphql.codegen.AddConsignment.{addConsignment => ac}
import graphql.codegen.AddFileMetadata.{addFileMetadata => afm}
import graphql.codegen.AddFFIDMetadata.{addFFIDMetadata => affm}
import graphql.codegen.AddTransferAgreement.{AddTransferAgreement => ata}
import graphql.codegen.GetSeries.{getSeries => gs}
import graphql.codegen.GetConsignmentExport.{getConsignmentForExport => gcfe}
import graphql.codegen.types._
import uk.gov.nationalarchives.files.graphql.GraphqlUtility.MatchIdInfo
import uk.gov.nationalarchives.files.keycloak.UserCredentials
import io.circe.generic.auto._

import java.nio.file.Path

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
    val input = AddTransferAgreementInput(consignmentId, true, true, true, true, true, true)
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
}

object GraphqlUtility {
  case class MatchIdInfo(checksum: String, path: Path, matchId: Int)
  def apply(userCredentials: UserCredentials): GraphqlUtility = new GraphqlUtility(userCredentials)
}
