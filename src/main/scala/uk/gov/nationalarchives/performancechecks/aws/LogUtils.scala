package uk.gov.nationalarchives.performancechecks.aws

import cats.effect.IO
import io.circe.parser.decode
import cats.implicits._
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsAsyncClient
import software.amazon.awssdk.services.cloudwatchlogs.model.{DeleteLogStreamRequest, DescribeLogStreamsRequest, GetLogEventsRequest, LogStream}
import uk.gov.nationalarchives.performancechecks.Main.{FileCheckResults, Result}
import uk.gov.nationalarchives.performancechecks.aws.STSUtils.assumeRoleProvider
import uk.gov.nationalarchives.performancechecks.aws.LambdaUtils.FutureUtils
import scala.concurrent.duration._

import scala.jdk.CollectionConverters._
import io.circe.generic.auto._

object LogUtils {
  case class Messages(lambdaName: String, messages: List[String])

  def client: CloudWatchLogsAsyncClient = CloudWatchLogsAsyncClient.builder.credentialsProvider(assumeRoleProvider).build()

  private def getLogEvents(logStream: LogStream, logGroupName: String) = {
    val request = GetLogEventsRequest.builder.logGroupName(logGroupName).logStreamName(logStream.logStreamName).build
    client.getLogEvents(request).toIO
  }

  private def getLogStreams(logGroupName: String, nextToken: Option[String] = None): IO[List[LogStream]] = {
    val request = if(nextToken.isDefined) {
      DescribeLogStreamsRequest.builder.logGroupName(logGroupName).nextToken(nextToken.get).build
    } else {
      DescribeLogStreamsRequest.builder.logGroupName(logGroupName).build
    }
    for {
      res <- client.describeLogStreams(request).toIO
      streams <- if(res.nextToken== null) {
        IO.sleep(2.seconds) >> IO(res.logStreams.asScala.toList)
      } else {
        IO.sleep(2.second) >> getLogStreams(logGroupName, Some(res.nextToken)).flatMap(s => IO(s ++ res.logStreams.asScala.toList))
      }
    } yield streams
  }

  def deleteExistingLogStreams(lambdas: List[String]): IO[List[Unit]] = {
    lambdas.map(lambdaName => {
      val logGroupName = s"/aws/lambda/tdr-$lambdaName-sbox"
      for {
        result <- getLogStreams(logGroupName)
        _ <- result.map(logStream => {
          val deleteRequest = DeleteLogStreamRequest.builder
            .logGroupName(logGroupName)
            .logStreamName(logStream.logStreamName)
            .build
          client.deleteLogStream(deleteRequest).toIO >> IO.sleep(500.milliseconds)
        }).sequence
      } yield ()
    } >> IO.sleep(1.second)).sequence
  }

  def getMessages(lambdas: List[String]): IO[List[Messages]] = lambdas.map(lambdaName => {
    val logGroupName = s"/aws/lambda/tdr-$lambdaName-sbox"
    for {
      result <- getLogStreams(logGroupName)
      logEvents <- result.map(logStream => IO.sleep(500.milliseconds) >> getLogEvents(logStream, logGroupName)).sequence
    } yield {
      val messages = for {
        logEvent <- logEvents
        event <- logEvent.events.asScala
      } yield event.message
      Messages(lambdaName, messages)
    }
  }).sequence

  def getResults(lambdas: List[String]): IO[List[FileCheckResults]] = for {
    messages <- getMessages(lambdas)
  } yield messages.map(message => {
    val results = message.messages.map(message => decode[Result](message))
      .collect {
        case Right(value) => value
      }
    FileCheckResults(message.lambdaName.replace("-", "_"), results)
  })
}
