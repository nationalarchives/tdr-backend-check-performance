package uk.gov.nationalarchives.files.aws

import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import software.amazon.awssdk.services.sts.model.{AssumeRoleRequest, Credentials}

object STSUtils {
  lazy val stsClient: StsClient = StsClient.builder.build()

  val sandboxAccountNumber: String = SystemsManagerUtils.managementParameter("/mgmt/sandbox_account")
  val managementAccountNumber: String = stsClient.getCallerIdentity.account()
  val request: AssumeRoleRequest = AssumeRoleRequest.builder.roleArn(s"arn:aws:iam::$sandboxAccountNumber:role/TDRTerraformRoleSbox").roleSessionName("performance").build()

  def assumeRoleCredentials: Credentials = stsClient.assumeRole(request).credentials()

  def assumeRoleProvider: StsAssumeRoleCredentialsProvider = {
    StsAssumeRoleCredentialsProvider.builder.refreshRequest(request).stsClient(stsClient).build()
  }

}
