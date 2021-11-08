package uk.gov.nationalarchives.performancechecks.aws

import cats.effect.IO
import uk.gov.nationalarchives.performancechecks.aws.LambdaUtils.FutureUtils
import cats.implicits._
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2AsyncClient
import software.amazon.awssdk.services.elasticloadbalancingv2.model.{DescribeLoadBalancersRequest, DescribeTargetGroupsRequest, DescribeTargetHealthRequest, LoadBalancer, LoadBalancerAttribute, ModifyLoadBalancerAttributesRequest, TargetHealthStateEnum}

import scala.jdk.CollectionConverters._

object  LoadBalancerUtils {
  def client: ElasticLoadBalancingV2AsyncClient = ElasticLoadBalancingV2AsyncClient.builder
    .credentialsProvider(STSUtils.assumeRoleProvider)
    .build()

  def waitForServices(services: List[String]): IO[List[Unit]] = {
    services.map(service => {
      for {
        loadBalancersResponse <- client.describeLoadBalancers(DescribeLoadBalancersRequest.builder.names(s"tdr-$service-sbox").build()).toIO
        targetGroups <- client.describeTargetGroups(DescribeTargetGroupsRequest.builder.loadBalancerArn(loadBalancersResponse.loadBalancers.asScala.head.loadBalancerArn).build).toIO
        _ <- client.waiter().waitUntilTargetInService(DescribeTargetHealthRequest.builder.targetGroupArn(targetGroups.targetGroups.asScala.head.targetGroupArn).build).toIO
      } yield ()
    }).sequence
  }

  private def deletionProtectionFalse(loadBalancer: LoadBalancer) = {
    val request = ModifyLoadBalancerAttributesRequest.builder
      .loadBalancerArn(loadBalancer.loadBalancerArn)
      .attributes(LoadBalancerAttribute.builder.key("deletion_protection.enabled").value("false").build)
      .build

    client.modifyLoadBalancerAttributes(request).toIO
  }

  def removeDeletionProtection(services: List[String]): IO[List[Unit]] = {
    services.map(service => {
      for {
        loadBalancers <- client.describeLoadBalancers(DescribeLoadBalancersRequest.builder.names(s"tdr-$service-sbox").build()).toIO
        _ <- loadBalancers.loadBalancers.asScala.toList.map(deletionProtectionFalse).sequence
      } yield ()
    }).sequence
  }
}
