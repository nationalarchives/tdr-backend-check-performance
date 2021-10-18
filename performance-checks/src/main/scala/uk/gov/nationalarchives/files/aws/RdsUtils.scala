package uk.gov.nationalarchives.files.aws

import cats.effect.IO
import software.amazon.awssdk.services.rds.RdsAsyncClient
import software.amazon.awssdk.services.rds.model.{DBCluster, DescribeDbClustersRequest, ModifyDbClusterRequest, ModifyDbClusterResponse}
import uk.gov.nationalarchives.files.aws.Credentials.assumeRoleProvider
import uk.gov.nationalarchives.files.aws.LambdaUtils.FutureUtils
import cats.implicits._

import scala.jdk.CollectionConverters._

object RdsUtils {
  private def client = RdsAsyncClient.builder.credentialsProvider(assumeRoleProvider).build()

  private def noDeletionProtection(cluster: DBCluster): IO[ModifyDbClusterResponse] = {
    val request = ModifyDbClusterRequest.builder
      .dbClusterIdentifier(cluster.dbClusterIdentifier)
      .applyImmediately(true)
      .deletionProtection(false)
      .build()
    client.modifyDBCluster(request).toIO
  }

  def removeDeletionProtection(): IO[Unit] = {
    for {
      dbClusters <- client.describeDBClusters(DescribeDbClustersRequest.builder.build).toIO
      _ <- dbClusters.dbClusters.asScala.toList.map(noDeletionProtection).sequence
    } yield ()
  }


}
