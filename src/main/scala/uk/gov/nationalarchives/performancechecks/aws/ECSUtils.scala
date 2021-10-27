package uk.gov.nationalarchives.performancechecks.aws

import cats.effect.IO
import software.amazon.awssdk.services.ec2.Ec2AsyncClient
import software.amazon.awssdk.services.ec2.model._
import software.amazon.awssdk.services.ecs.EcsAsyncClient
import software.amazon.awssdk.services.ecs.model._
import uk.gov.nationalarchives.performancechecks.aws.STSUtils.assumeRoleProvider
import uk.gov.nationalarchives.performancechecks.aws.LambdaUtils.FutureUtils

import java.util
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

object ECSUtils {
  val clusterName = "file_format_build_sbox"
  val taskName = "file-format-build-sbox"
  def runFileFormatTask(): IO[String] = {
    val ec2Client = Ec2AsyncClient.builder.credentialsProvider(assumeRoleProvider).build()
    val ecsClient = EcsAsyncClient.builder.credentialsProvider(assumeRoleProvider).build()
    val describeSecurityGroupsRequest = DescribeSecurityGroupsRequest.builder
      .filters(Filter.builder.name("group-name").values("allow-ecs-mount-efs").build())
      .build()

    val describeSubnetsRequest = DescribeSubnetsRequest.builder
      .filters(Filter.builder.name("tag:Name").values("tdr-private-subnet-0-sbox", "tdr-private-subnet-1-sbox").build())
      .build()

    def runTaskRequest(securityGroups: DescribeSecurityGroupsResponse, subnets: DescribeSubnetsResponse) = {
      val securityGroupIds = securityGroups.securityGroups.asScala.map(_.groupId).asJava
      val subnetIds: util.Collection[String] = subnets.subnets.asScala.map(_.subnetId).asJava
      val networkConfiguration = NetworkConfiguration.builder
        .awsvpcConfiguration(
          AwsVpcConfiguration.builder
            .securityGroups(securityGroupIds)
            .subnets(subnetIds)
            .build()
        ).build

      RunTaskRequest.builder
        .cluster(clusterName)
        .taskDefinition(taskName)
        .launchType("FARGATE")
        .platformVersion("1.4.0")
        .networkConfiguration(networkConfiguration)
        .build
    }

    def waitForTaskToComplete(taskResponse: RunTaskResponse): IO[Boolean] = {
      val taskArns = taskResponse.tasks.asScala.map(_.taskArn).asJava
      val request = DescribeTasksRequest.builder
        .cluster(clusterName)
        .tasks(taskArns)
        .build
      for {
        tasks <- ecsClient.describeTasks(request).toIO
        stillRunning = tasks.tasks.asScala.toList.flatMap(_.containers.asScala.map(_.exitCode == 0)).contains(false)
        completed <- if(stillRunning) {
          IO.sleep(10.seconds).flatMap(_ => waitForTaskToComplete(taskResponse))
        } else {
          IO(true)
        }
      } yield completed
    }

    for {
      securityGroups <- ec2Client.describeSecurityGroups(describeSecurityGroupsRequest).toIO
      subnets <- ec2Client.describeSubnets(describeSubnetsRequest).toIO
      task <- ecsClient.runTask(runTaskRequest(securityGroups, subnets)).toIO
      _ <- waitForTaskToComplete(task)
    } yield task.tasks().asScala.head.taskArn()
  }
}
