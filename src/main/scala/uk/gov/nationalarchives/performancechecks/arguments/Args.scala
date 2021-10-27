package uk.gov.nationalarchives.performancechecks.arguments

import cats.implicits._
import com.monovore.decline._

import java.nio.file.Path

object Args {
  case class PerformanceChecks(files: List[Path], create: Boolean, results: Boolean, destroy: Boolean)

  val files: Opts[List[Path]] = Opts.arguments[Path]("file").orEmpty
  val createResources: Opts[Boolean] = Opts.flag("create-resources", "Whether to run terraform and deploy lambdas and docker images", "c").orFalse
  val createResults: Opts[Boolean] = Opts.flag("create-results", "Whether to run the performance checks and output the results", "r").orFalse
  val destroyResources: Opts[Boolean] = Opts.flag("destroy-resources", "Whether to remove deletion protection to allow resource destruction", "d").orFalse

  val performanceChecks: Opts[PerformanceChecks] = (files, createResources, createResults, destroyResources) mapN {
    (files, create, results, resources) => PerformanceChecks(files, create, results, resources)
  }
}
