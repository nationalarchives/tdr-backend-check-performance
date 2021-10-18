package uk.gov.nationalarchives.files.docker
import cats.effect.IO

import scala.sys.process._

object Docker {
  def login(accountNumber: String) = {
    val command = "aws ecr get-login-password --region eu-west-2" #| s"docker login --username AWS --password-stdin $accountNumber.dkr.ecr.eu-west-2.amazonaws.com"
    IO(command.!)
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
      _ <- login(managementAccountNumber)
      _ <- pull(DockerImage(imageName, managementAccountNumber, "intg"))
      _ <- tag(DockerImage(imageName, managementAccountNumber, "intg"), sandboxAccountNumber, "sbox")
      _ <- login(sandboxAccountNumber)
      _ <- push(DockerImage(imageName, sandboxAccountNumber, "intg"))
    } yield ()

  case class DockerImage(imageName: String, accountNumber: String, tag: String)
}
