package uk.gov.nationalarchives.performancechecks.retry

import cats.effect.IO
import retry.RetryDetails._
import retry.{RetryDetails, RetryPolicy, retryingOnAllErrors}
import retry.RetryPolicies.{exponentialBackoff, limitRetries}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Retry {
  private val retryPolicy: RetryPolicy[IO] = limitRetries[IO](5) join exponentialBackoff[IO](10.seconds)
  private def logError(err: Throwable, details: RetryDetails): IO[Unit] = details match {
    case WillDelayAndRetry(nextDelay, retriesSoFar, _) =>
      IO.println(s"Error ${err.getMessage} Retried $retriesSoFar times. Waiting ${nextDelay.toSeconds} to try again")
    case GivingUp(totalRetries: Int, _: FiniteDuration) =>
      IO.println(s"Giving up after $totalRetries retries")
  }

  def retry[T](fn: String => IO[T], args: String): IO[Unit] =
    retryingOnAllErrors[T](retryPolicy, logError)(fn(args)).map(_ => ())
}
