package uk.gov.nationalarchives.files.arguments

import cats.data.NonEmptyList
import cats.implicits._
import com.monovore.decline.Opts
import io.circe.generic.auto._
import io.circe.parser.decode

object Args {
  trait Performance {}
  case class CreateFiles(files: NonEmptyList[String]) extends Performance
  case class SaveResults(checkName: String, message: String) extends Performance
  case class Event(message: String)
  case class LogEvents(events: List[Event])

  val files: Opts[NonEmptyList[String]] = Opts.arguments[String]("file")
  val resultName: Opts[String] = Opts.option[String]("check-name", short = "c", help = "The name of the backend check lambda")
  val input: Opts[String] = Opts.option[String]("message", short = "m", help = "The message from cloudwatch")

  val performanceOpts: Opts[Performance] =
    Opts.subcommand("create-files", "Creates files in the database and uploads them to S3") {
      files map {
        f => CreateFiles(f)
      }
    } orElse Opts.subcommand("save-results", "Saves the results from the cloudwatch logs") {
      (resultName, input) mapN {
        (resultName, input) => SaveResults(resultName, input)
      }
    }
}
