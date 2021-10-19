package uk.gov.nationalarchives.files.arguments

import cats.implicits._
import com.monovore.decline._

import java.nio.file.Path

object Args {
  case class PerformanceChecks(files: List[Path], create: Boolean, results: Boolean, destroy: Boolean, terraform: Boolean)

  val files: Opts[List[Path]] = Opts.arguments[Path]("file").orEmpty
  val createResources: Opts[Boolean] = Opts.flag("create-resources", "Whether to run terraform and deploy lambdas and docker images", "c").orFalse
  val createResults: Opts[Boolean] = Opts.flag("create-results", "Whether to run the performance checks and output the results", "r").orFalse
  val destroyResources: Opts[Boolean] = Opts.flag("destroy-resources", "Whether to run terraform destroy", "d").orFalse
  val runTerraform: Opts[Boolean] = Opts.flag("run-terraform", "Whether to run terraform apply", "t").orFalse

  val performanceChecks: Opts[PerformanceChecks] = (files, createResources, createResults, destroyResources, runTerraform) mapN {
    (files, create, results, resources, terraform) => PerformanceChecks(files, create, results, resources, terraform)
  }
}
