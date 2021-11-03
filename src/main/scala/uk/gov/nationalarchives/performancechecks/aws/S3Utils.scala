package uk.gov.nationalarchives.performancechecks.aws

import cats.effect.IO
import cats.implicits._
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model._
import uk.gov.nationalarchives.performancechecks.api.GraphqlUtility._
import uk.gov.nationalarchives.performancechecks.aws.LambdaUtils.FutureUtils
import uk.gov.nationalarchives.performancechecks.aws.STSUtils.assumeRoleProvider

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.UUID
import scala.jdk.CollectionConverters._

object S3Utils {

  def downloadFiles(filePaths: List[Path]): IO[List[Unit]] = {
    new File("content").mkdir
    filePaths.map(filePath => {
      val bucket = "tdr-upload-test-data"
      val listRequest = ListObjectsV2Request.builder.prefix(filePath.toString).bucket(bucket).build

      def getObject(obj: S3Object): IO[GetObjectResponse] = {
        val getRequest = GetObjectRequest.builder.bucket(bucket).key(obj.key).build
        client.getObject(getRequest, Paths.get("content", obj.key.split("/").last)).toIO
      }
      for {
        objs <- client.listObjectsV2(listRequest).toIO
        _ <- objs.contents.asScala.toList.map(getObject).sequence
      } yield ()
    }).sequence
  }

  def client: S3AsyncClient = S3AsyncClient.builder
    .credentialsProvider(assumeRoleProvider)
    .build()

  def uploadLambdaFile(key: String): IO[PutObjectResponse] = {
    val bucket = "tdr-backend-checks-sbox"
    val request = PutObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .build
    client.putObject(request, Paths.get(key)).toIO
  }

  def uploadConsignmentFiles(userId: UUID, consignmentData: ConsignmentData): IO[List[PutObjectResponse]] = {
    val idMap = consignmentData.matchIdInfo.map(m => m.matchId -> m.path).toMap

    consignmentData.files.map(f => {
      val request = PutObjectRequest.builder()
        .bucket("tdr-upload-files-cloudfront-dirty-sbox")
        .key(s"$userId/${consignmentData.consignmentId}/${f.fileId}")
        .build
      client.putObject(request, idMap(f.matchId.toInt)).toIO
    }).sequence
  }
}
