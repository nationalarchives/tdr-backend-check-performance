package uk.gov.nationalarchives.files.html

import cats.effect.IO
import scalatags.Text.all._
import uk.gov.nationalarchives.files.database.Database.{AggregateResult, AggregateResults}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.io.Source
import shapeless._

class HtmlReport(aggregateResults: List[AggregateResults]) {
  case class TimeTakenByCheck(check: String, timeTaken: Double)

  implicit class IntWithTimes[T](list: List[T]) {
    val stringList: TypeCase[List[String]] = TypeCase[List[String]]
    val doubleList: TypeCase[List[Double]] = TypeCase[List[Double]]

    def toJSArray(): String = list match {
      case stringList(sl) => s"[${sl.map(l => s"'$l'").mkString(",")}]"
      case doubleList(il) =>  s"[${il.mkString(",")}]"
    }
  }

  def groupValues(labelName: String, fn: AggregateResult => String): List[(String, String)] = {
    val labels = aggregateResults.flatMap(_.results.map(fn)).distinct.sorted
    aggregateResults.map(results => {
      val labelMap: Map[String, Double] = results.results.groupBy(fn) map {
        case (label, results) =>
          (label, results.map(_.timeTaken).sum / results.size)
      }
      (s"${results.checkName.replace(" ", "")}${labelName}Data", labels.map(label => labelMap(label)).toJSArray())
    }) ++ List((s"${labelName}Labels", labels.toJSArray()))
  }

  def createReport(): IO[Unit] = IO {
    val checkNameLabels = aggregateResults.map(_.checkName).toJSArray()
    val timeByCheckData = aggregateResults.map(result => result.results.map(_.timeTaken).sum / result.results.size).toJSArray()

    val javascript = Source.fromResource("javascript/index.js")
      .getLines.mkString

    val groupedValues =
      groupValues("FileType", _.fileType) ++ groupValues("FileSize", _.fileSize.toString)

    val valuesToReplace: List[(String, String)] = groupedValues ++ List(("checkNameLabels", checkNameLabels), ("timeByCheckData", timeByCheckData))

    val finalJs = valuesToReplace.foldLeft(javascript)((js, values) => js.replace(values._1, values._2))

    val report: String = html(
      head(
        link(rel := "stylesheet", href := "https://jenkins.tdr-management.nationalarchives.gov.uk/userContent/bootstrap.min.css"),
        script(src := "https://jenkins.tdr-management.nationalarchives.gov.uk/userContent/charts.min.js"),
        script(finalJs),
        body(
          div(`class` := "container",
            for (result <- aggregateResults) yield {
              div(`class` := "row",
                div(`class` := "col-sm",
                  h3(s"${result.checkName} (Top 10 results)  ",
                    a(href := s"${result.checkName.replace(" ", "").toLowerCase}.csv")("Download")
                  ),
                  table(`class` := "table",
                    thead(
                      tr(
                        th(scoped := "col")("File Path"),
                        th(scoped := "col")("File Size (bytes)"),
                        th(scoped := "col")(s"Time Taken (seconds)"),
                        th(scoped := "col")(s"Number of executions")
                      )
                    ),
                    tbody(
                      for (row <- result.results.sortBy(-_.timeTaken)) yield
                        tr(
                          td(row.filePath),
                          td(row.fileSize),
                          td(row.timeTaken),
                          td(row.numberOfExecutions)
                        )
                    )
                  )
                )
              )
            },
            h3("Average time taken per file check"),
            canvas(id := "timeTaken"),
            h3("Average time taken per file check by file type"),
            canvas(id := "timeByFileType"),
            h3("Average time taken per file check by file size"),
            canvas(id := "timeByFileSize")
          )
        )
      )
    ).render
    Files.write(Paths.get("output.html"), report.getBytes(StandardCharsets.UTF_8))
  }
}
object HtmlReport {
  def apply(aggregateResults: List[AggregateResults]) = new HtmlReport(aggregateResults)
}
