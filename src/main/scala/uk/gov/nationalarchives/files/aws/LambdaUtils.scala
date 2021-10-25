package uk.gov.nationalarchives.files.aws

import cats.effect.IO
import cats.implicits._
import sttp.client3.UriContext
import sttp.client3._
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.circe.asJson
import io.circe.generic.auto._
import software.amazon.awssdk.services.lambda.{LambdaAsyncClient, LambdaClient}
import software.amazon.awssdk.services.lambda.model.{InvokeRequest, InvokeResponse, UpdateFunctionCodeRequest, UpdateFunctionCodeResponse}
import sttp.model.Uri
import uk.gov.nationalarchives.files.Main.Lambda
import uk.gov.nationalarchives.files.aws.STSUtils.assumeRoleProvider

import java.io.File
import java.util.concurrent.CompletableFuture
import scala.jdk.FutureConverters.CompletionStageOps
import scala.concurrent.duration._

object LambdaUtils {
  def lambdaClient = LambdaAsyncClient.builder.credentialsProvider(assumeRoleProvider).build()
  case class Asset(browser_download_url: String)
  case class Releases(assets: List[Asset])

  implicit class FutureUtils[T](f: CompletableFuture[T]) {
    def toIO: IO[T] = IO.fromFuture(IO(f.asScala))
  }


  def updateFunctionCode(lambdaName: String, fileName: String) = {
    val request = UpdateFunctionCodeRequest.builder
      .functionName(lambdaName)
      .s3Key(fileName)
      .s3Bucket("tdr-backend-checks-sbox")
      .build()
    lambdaClient.updateFunctionCode(request).toIO
  }

  def invokeLambda(lambdaName: String): IO[InvokeResponse] = {
    lambdaClient.invoke(InvokeRequest.builder.functionName(s"tdr-$lambdaName-sbox").build()).toIO.flatMap(response => {
      if(response.statusCode() == 200 && response.functionError() == null) {
        IO.println(response) >> IO(response)
      } else {
        IO.raiseError(new Exception(""))
      }
    })
  }

  def updateLambdas(lambdas: List[Lambda]): IO[List[UpdateFunctionCodeResponse]] = {
    AsyncHttpClientCatsBackend.resource[IO]().use { backend =>
      def request(lambdaName: String) = basicRequest
        .get(uri"https://api.github.com/repos/nationalarchives/tdr-$lambdaName/releases/latest")
        .response(asJson[Releases])

      def download(uri: Uri) =
        basicRequest.get(uri)
          .response(asFile(new File(uri.toString().split("/").last)))

      lambdas.map(l => {
        val lambdaName = l.lambdaName.getOrElse(l.repoName)
        for {
          res <- backend.send(request(l.repoName))
          body <- IO.fromEither(res.body)
          fileResponse <- backend.send(download(uri"${body.assets.head.browser_download_url}"))
          file <- IO.fromEither(fileResponse.body.left.map(err => new Exception(err)))
          _ <- IO(S3Utils.uploadLambdaFile(file.getPath))
          lambdaResponse <- updateFunctionCode(s"tdr-$lambdaName-sbox", file.getPath)
          _ <- IO.println(lambdaResponse)
          _ <- IO.sleep(20.seconds) //Wait before invoking lambdas as this can fail
        } yield {
          lambdaResponse
        }
      }).sequence
    }
  }


}
