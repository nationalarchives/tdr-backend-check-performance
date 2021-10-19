package uk.gov.nationalarchives.files.docker
import cats.effect.IO
import software.amazon.awssdk.services.ecr.EcrAsyncClient
import software.amazon.awssdk.services.ecr.model.{GetAuthorizationTokenRequest, GetAuthorizationTokenResponse}
import uk.gov.nationalarchives.files.aws.STSUtils.{assumeRoleProvider, managementAccountNumber, sandboxAccountNumber}
import uk.gov.nationalarchives.files.aws.LambdaUtils.FutureUtils

import java.util.Base64
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.sys.process._

object Docker {

  private def login(client: EcrAsyncClient, accountNumber: String) = {
    for {
      authToken <- client.getAuthorizationToken(GetAuthorizationTokenRequest.builder.build()).toIO
    } yield {
      val token = new String(Base64.getDecoder.decode(authToken.authorizationData.asScala.head.authorizationToken))
      val password = token.split(":").last
      val process = s"echo $password" #| s"docker login --username AWS --password-stdin $accountNumber.dkr.ecr.eu-west-2.amazonaws.com"
      process.!
    }
  }

  def sandboxLogin(): IO[Int] = {
    login(EcrAsyncClient.builder.credentialsProvider(assumeRoleProvider).build(), sandboxAccountNumber)
  }

  def managementLogin(): IO[Int] = {
    login(EcrAsyncClient.builder.build(), managementAccountNumber)
  }

  private def image(accountNumber: String, imageName: String, tag: String) = s"$accountNumber.dkr.ecr.eu-west-2.amazonaws.com/$imageName:$tag"

  private def command(dockerCommand: String, dockerImage: DockerImage) = {
    val commandImage = image(dockerImage.accountNumber, dockerImage.imageName, dockerImage.tag)
    IO(s"docker $dockerCommand $commandImage".!)
  }

  def pull(dockerImage: DockerImage) = command("pull", dockerImage)

  def push(dockerImage: DockerImage) = command("push", dockerImage)

  def tag(dockerImage: DockerImage, accountNumberTo: String, tagTo: String) = {
    val imageFrom = image(dockerImage.accountNumber, dockerImage.imageName, dockerImage.tag)
    val imageTo = image(accountNumberTo, dockerImage.imageName, tagTo)
    IO(s"docker tag $imageFrom $imageTo".!)
  }

  def updateDockerImages(imageName: String, managementAccountNumber: String, sandboxAccountNumber: String) =
    for {
      _ <- managementLogin()
      _ <- pull(DockerImage(imageName, managementAccountNumber, "intg"))
      _ <- tag(DockerImage(imageName, managementAccountNumber, "intg"), sandboxAccountNumber, "sbox")
      _ <- sandboxLogin()
      _ <- push(DockerImage(imageName, sandboxAccountNumber, "sbox"))
    } yield ()

  case class DockerImage(imageName: String, accountNumber: String, tag: String)
}
