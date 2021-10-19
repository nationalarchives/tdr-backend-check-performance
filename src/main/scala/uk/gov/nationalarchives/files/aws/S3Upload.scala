package uk.gov.nationalarchives.files.aws

import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{PutObjectRequest, PutObjectResponse}
import uk.gov.nationalarchives.files.api.GraphqlUtility._
import uk.gov.nationalarchives.files.aws.STSUtils.assumeRoleProvider

import java.nio.file.Paths
import java.util.UUID

object S3Upload {

  def client: S3Client = S3Client.builder
    .credentialsProvider(assumeRoleProvider)
    .build()

  def uploadLambdaFile(key: String): PutObjectResponse = {
    val bucket = "tdr-backend-checks-sbox"
    val request = PutObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .build
    client.putObject(request, Paths.get(key))
  }

  def uploadConsignmentFiles(userId: UUID, consignmentData: ConsignmentData): List[PutObjectResponse] = {
    val idMap = consignmentData.matchIdInfo.map(m => m.matchId -> m.path).toMap


    consignmentData.files.map(f => {
      val request = PutObjectRequest.builder()
        .bucket("tdr-upload-files-cloudfront-dirty-sbox")
        .key(s"$userId/${consignmentData.consignmentId}/${f.fileId}")
        .build
      client.putObject(request, idMap(f.matchId.toInt))
    })
  }
}
