package uk.gov.nationalarchives.performancechecks.aws

import cats.effect.IO
import uk.gov.nationalarchives.performancechecks.aws.LambdaUtils.FutureUtils
import cats.implicits._
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2AsyncClient
import software.amazon.awssdk.services.elasticloadbalancingv2.model.{DescribeLoadBalancersRequest, DescribeTargetGroupsRequest, DescribeTargetHealthRequest, LoadBalancer, LoadBalancerAttribute, ModifyLoadBalancerAttributesRequest, TargetHealthStateEnum}

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

object  LoadBalancerUtils {
  def client = ElasticLoadBalancingV2AsyncClient.builder
    .credentialsProvider(STSUtils.assumeRoleProvider)
    .build()

  private def areServicesHealthy(services: List[String]) = {
    services.map(service => {
      for {
        loadBalancersResponse <- client.describeLoadBalancers(DescribeLoadBalancersRequest.builder.names(s"tdr-$service-sbox").build()).toIO
        targetGroups <- client.describeTargetGroups(DescribeTargetGroupsRequest.builder.loadBalancerArn(loadBalancersResponse.loadBalancers.asScala.head.loadBalancerArn).build).toIO
        targetHealth <- client.describeTargetHealth(DescribeTargetHealthRequest.builder.targetGroupArn(targetGroups.targetGroups.asScala.head.targetGroupArn).build).toIO
      } yield targetHealth.targetHealthDescriptions().asScala.count(_.targetHealth.state == TargetHealthStateEnum.HEALTHY) > 0
    }).sequence
  }

  def waitForServices(services: List[String]): IO[Boolean] = {
    for {
      areHealthy <- areServicesHealthy(services)
      healthy <- if(areHealthy.count(healthy => healthy) == services.size) {
        IO(true)
      } else {
        IO.sleep(10.seconds).flatMap(_ => waitForServices(services))
      }
    } yield healthy
  }

  private def deletionProtectionFalse(loadBalancer: LoadBalancer) = {
    val request = ModifyLoadBalancerAttributesRequest.builder
      .loadBalancerArn(loadBalancer.loadBalancerArn)
      .attributes(LoadBalancerAttribute.builder.key("deletion_protection.enabled").value("false").build)
      .build

    client.modifyLoadBalancerAttributes(request).toIO
  }

  def removeDeletionProtection(services: List[String]) = {
    services.map(service => {
      for {
        loadBalancers <- client.describeLoadBalancers(DescribeLoadBalancersRequest.builder.names(s"tdr-$service-sbox").build()).toIO
        _ <- loadBalancers.loadBalancers.asScala.toList.map(deletionProtectionFalse).sequence
      } yield ()
    }).sequence
  }
}
