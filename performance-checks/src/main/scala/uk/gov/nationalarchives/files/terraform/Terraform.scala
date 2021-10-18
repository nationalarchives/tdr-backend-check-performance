package uk.gov.nationalarchives.files.terraform

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import uk.gov.nationalarchives.files.aws.Credentials.assumeRoleCredentials

import scala.sys.process._

object Terraform {
  val command: String = ConfigFactory.load().getString("terraform.command")

  private def command(terraformArg: String): IO[String] = {
    val credentials = assumeRoleCredentials
    val envCredentials = List(
      "AWS_ACCESS_KEY_ID" -> credentials.accessKeyId,
      "AWS_SECRET_ACCESS_KEY" -> credentials.secretAccessKey,
      "AWS_SESSION_TOKEN" -> credentials.sessionToken
    )
    IO(Process(s"$command $terraformArg --auto-approve", None, envCredentials: _*).!!)
  }

  def apply(): IO[String] = command("apply")

  def destroy(): IO[String] = command("destroy")
}
