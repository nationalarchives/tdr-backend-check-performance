package uk.gov.nationalarchives.files.html

import cats.effect.IO
import scalatags.Text.all._
import uk.gov.nationalarchives.files.database.Database.AggregateResults

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object HtmlReport {
  case class TimeTakenByCheck(check: String, timeTaken: Double)

  def createReport(aggregateResults: List[AggregateResults]): IO[Unit] = IO {
    val checkNameLabels = s"[${aggregateResults.map(result => s"""'${result.checkName}'""").mkString(",")}]"
    val timeByCheckData = s"[${aggregateResults.map(result => result.results.map(_.timeTaken).sum / result.results.size).mkString(",")}]"
    val report = html(
      head(
        link(rel := "stylesheet", href := "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css"),
        script(src := "https://cdn.jsdelivr.net/npm/chart.js@3.5.1/dist/chart.min.js"),
        script(
          s"""
             |window.addEventListener('load', function () {
             |  const data = {
             |    labels: $checkNameLabels,
             |    datasets: [{
             |      label: 'Average time taken per file check',
             |      data: $timeByCheckData,
             |      backgroundColor: [
             |        'rgba(255, 99, 132, 0.2)',
             |        'rgba(255, 159, 64, 0.2)',
             |        'rgba(255, 205, 86, 0.2)',
             |        'rgba(75, 192, 192, 0.2)',
             |        'rgba(54, 162, 235, 0.2)'
             |      ],
             |    }]
             |  };
             |  const config = {
             |    type: 'bar',
             |    data: data,
             |  };
             |  new Chart(
             |    document.getElementById('timeTaken'),
             |    config
             |  );
             |}, false);
             |""".stripMargin)),
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
                    for (row <- result.results.sortBy(- _.timeTaken)) yield
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
          canvas(id := "timeTaken")
        )
      )
    ).render
    Files.write(Paths.get("output.html"), report.getBytes(StandardCharsets.UTF_8))
  }
}
