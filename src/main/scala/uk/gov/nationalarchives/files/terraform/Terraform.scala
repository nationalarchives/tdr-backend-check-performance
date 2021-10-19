package uk.gov.nationalarchives.files.terraform

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import uk.gov.nationalarchives.files.aws.STSUtils.assumeRoleCredentials

import java.io.File
import scala.sys.process._

object Terraform {
  val terraformCommand: String = ConfigFactory.load().getString("terraform.command")

  private def command(terraformArg: String) = {
    val credentials = assumeRoleCredentials
    val envCredentials = List(
      "AWS_ACCESS_KEY_ID" -> credentials.accessKeyId,
      "AWS_SECRET_ACCESS_KEY" -> credentials.secretAccessKey,
      "AWS_SESSION_TOKEN" -> credentials.sessionToken
    )
    val file = new File("terraform")
    val process = Process(s"$terraformCommand $terraformArg --auto-approve", file, envCredentials: _*).run(ProcessLogger(s =>
      println(s)
    ))
    def checkIfFinished(): Int = {
      if(!process.isAlive()) {
        process.exitValue()
      } else {
        Thread.sleep(10000)
        checkIfFinished()
      }
    }
    val statusCode = checkIfFinished()
    if(statusCode != 0) {
      IO.raiseError(new Exception(s"Terraform exited with status code $statusCode"))
    } else {
      IO(statusCode)
    }
  }

  def apply(): IO[Int] = command("apply")

  def destroy(): IO[Int] = command("destroy")
}
