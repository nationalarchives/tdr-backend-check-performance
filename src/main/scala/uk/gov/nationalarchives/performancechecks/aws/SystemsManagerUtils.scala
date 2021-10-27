package uk.gov.nationalarchives.performancechecks.aws

import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import uk.gov.nationalarchives.performancechecks.aws.STSUtils.assumeRoleProvider

object SystemsManagerUtils {
  def managementParameter(name: String): String = {
    val ssmClient = SsmClient.builder.build()
    ssmClient.getParameter(GetParameterRequest.builder.name(name).withDecryption(true).build()).parameter().value()
  }

  def sandboxParameter(name: String): String = {
    val ssmClient = SsmClient.builder.credentialsProvider(assumeRoleProvider).build()
    ssmClient.getParameter(GetParameterRequest.builder.name(name).withDecryption(true).build()).parameter().value()
  }
}
