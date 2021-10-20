package uk.gov.nationalarchives.files.terraform

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import uk.gov.nationalarchives.files.aws.STSUtils.assumeRoleCredentials

import java.io.File
import scala.sys.process._

object Terraform {
  trait Command {
    def autoApprove: Boolean = false
    val autoApproveArg: String = if(autoApprove) "--auto-approve" else ""
    def command: String
    def commandString: String = s"$command $autoApproveArg"
  }
  case class Init(command: String = "init") extends Command
  case class Apply(command: String = "apply", override val autoApprove: Boolean) extends Command
  case class Destroy(command: String = "destroy", override val autoApprove: Boolean) extends Command

  val terraformCommand: String = ConfigFactory.load().getString("terraform.command")
  def envCredentials: List[(String, String)] = {
    val credentials = assumeRoleCredentials
    List(
      "AWS_ACCESS_KEY_ID" -> credentials.accessKeyId,
      "AWS_SECRET_ACCESS_KEY" -> credentials.secretAccessKey,
      "AWS_SESSION_TOKEN" -> credentials.sessionToken,
      "TF_CLI_ARGS" -> "-no-color"
    )
  }

  private def command(command: Command) = {

    val file = new File("terraform")
    val process = Process(s"$terraformCommand ${command.commandString}", file, envCredentials: _*).run(ProcessLogger(s =>
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

  def init(): IO[Int] = command(Init())

  def apply(): IO[Int] = command(Apply(autoApprove = true))

  def destroy(): IO[Int] = command(Destroy(autoApprove = true))
}
