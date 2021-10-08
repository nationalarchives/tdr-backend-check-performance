package uk.gov.nationalarchives.files.aws

import graphql.codegen.AddFilesAndMetadata.AddFilesAndMetadata.AddFilesAndMetadata
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{PutObjectRequest, PutObjectResponse}
import uk.gov.nationalarchives.files.graphql.GraphqlUtility.MatchIdInfo

import java.util.UUID

object S3Upload {
  def upload(userId: UUID, consignmentId: UUID, filesAndMetadata: List[AddFilesAndMetadata], matchIdInfo: List[MatchIdInfo]): List[PutObjectResponse] = {
    val idMap = matchIdInfo.map(m => m.matchId -> m.path).toMap
    val client = S3Client.builder
      .credentialsProvider(ProfileCredentialsProvider.builder().profileName("sandbox").build)
      .build()
    filesAndMetadata.map(f => {
      val request = PutObjectRequest.builder()
        .bucket("tdr-upload-files-cloudfront-dirty-sbox")
        .key(s"$userId/$consignmentId/${f.fileId}")
        .build
      client.putObject(request, idMap(f.matchId.toInt))
    })
  }
}
