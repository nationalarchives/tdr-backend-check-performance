package uk.gov.nationalarchives.files.database

import cats.effect.IO
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor.Aux
import uk.gov.nationalarchives.files.Main.FileCheckResults
import uk.gov.nationalarchives.files.api.GraphqlUtility.ConsignmentData
import uk.gov.nationalarchives.files.database.Database.{AggregateResult, AggregateResults}

import scala.math.BigDecimal.RoundingMode

class Database(xa: Aux[IO, Unit], fileCheckNames: List[String]) {

  def createTables(): IO[List[Int]] = {

    val dropFiles = sql"""DROP TABLE IF EXISTS files;""".update.run
    val createFiles =
      sql"""
    CREATE TABLE files (
      consignmentId TEXT NOT NULL,
      fileId  TEXT NOT NULL,
      filePath TEXT NOT NULL,
      fileSize NUMERIC
    )
  """.update.run


    def createTimeTable(fileCheck: String) = Fragment(s"CREATE TABLE $fileCheck", Nil) ++ fr" (fileId  TEXT NOT NULL, timeTaken INTEGER)"
    def deleteTimeTable(fileCheck: String) = Fragment(s"DROP TABLE IF EXISTS $fileCheck", Nil)
    fileCheckNames.map(name => for {
      _ <- (dropFiles, createFiles).mapN(_ + _).transact(xa)
      _ <- deleteTimeTable(name).update.run.transact(xa)
      c <- createTimeTable(name).update.run.transact(xa)
    } yield c).sequence
  }

  def insertFiles(consignment: ConsignmentData): IO[List[Int]] = {
    def filesInsert(consignmentId: String, fileId: String, filePath: String, fileSize: Long): doobie.ConnectionIO[Int] =
      sql"INSERT INTO files (consignmentId, fileId, filePath, fileSize) values ($consignmentId, $fileId, $filePath, $fileSize)".update.run

    consignment.files.map(file => {
      val matchedFile = consignment.matchIdInfo.find(_.matchId == file.matchId).head
      filesInsert(consignment.consignmentId.toString, file.fileId.toString, matchedFile.path.toString, matchedFile.fileSize).transact(xa)
    }).sequence
  }

  def insertResults(fileCheckResults: List[FileCheckResults]): IO[List[Int]] = {
    fileCheckResults.flatMap(fileCheck => {
      def insert(fileId: String, timeTaken: Double) = Fragment(s"INSERT INTO ${fileCheck.fileCheckName} ", List()) ++ fr"(fileId, timeTaken) values ($fileId, $timeTaken)"
      fileCheck.results.map(result => insert(result.fileId.toString, result.timeTaken).update.run.transact(xa))
    }).sequence
  }

  def getAggregateResults() = {
    fileCheckNames.map(fileCheck => {
      val checkName = fileCheck.split("_").map(_.capitalize).mkString(" ")
      Fragment(s"select f.filePath, f.fileSize, avg(timeTaken), count(*) from files f JOIN $fileCheck c on c.fileId = f.fileId group by 1,2", Nil).query[(String, Long, Double, Long)].to[List].transact(xa).map(results => {
        val resultList = results.map(res => {
          val (filePath, fileSize, timeTaken, count) = res
          AggregateResult(filePath, fileSize, BigDecimal(timeTaken).setScale(3, RoundingMode.HALF_UP).toDouble, count)
        })
        AggregateResults(checkName, resultList)
      })
    }).sequence
  }
}

object Database {
  case class AggregateResults(checkName: String, results: List[AggregateResult])
  case class AggregateResult(filePath: String, fileSize: Long, timeTaken: Double, count: Long)
  def apply(fileCheckNames: List[String]) = new Database(
    Transactor.fromDriverManager[IO](
      "org.sqlite.JDBC", "jdbc:sqlite:performance.db", "", ""
    ),
    fileCheckNames.map(_.replace("-", "_"))
  )
}
