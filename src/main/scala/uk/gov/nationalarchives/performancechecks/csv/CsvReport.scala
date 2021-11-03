package uk.gov.nationalarchives.performancechecks.csv

import cats.effect.IO
import io.chrisdavenport.cormorant._
import io.chrisdavenport.cormorant.generic.semiauto._
import io.chrisdavenport.cormorant.implicits._
import uk.gov.nationalarchives.performancechecks.database.Database.{AggregateResult, AggregateResults}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

object CsvReport {
  implicit val lw: LabelledWrite[AggregateResult] = deriveLabelledWrite

  def csvReport(aggregateResults: List[AggregateResults]): IO[List[Path]] = IO {
    aggregateResults.map(result => {
      val csv = result.results.writeComplete.print(Printer.default)
      Files.write(Paths.get(s"report/${result.checkName.replace(" ", "").toLowerCase}.csv"), csv.getBytes(StandardCharsets.UTF_8))
    })
  }
}
