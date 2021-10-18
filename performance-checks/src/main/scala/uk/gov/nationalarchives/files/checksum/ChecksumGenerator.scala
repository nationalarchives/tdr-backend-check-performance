package uk.gov.nationalarchives.files.checksum

import cats.effect.{IO, Resource}
import uk.gov.nationalarchives.files.api.GraphqlUtility.MatchIdInfo

import java.io.{File, FileInputStream}
import java.nio.file.Path
import java.security.MessageDigest

object ChecksumGenerator {
  def generate(path: String, index: Int): IO[MatchIdInfo] = {
    val chunkSizeInMB = 5
    val chunkSizeInBytes: Int = chunkSizeInMB * 1024 * 1024
    val messageDigester: MessageDigest = MessageDigest.getInstance("SHA-256")
    for {
      _ <- {
        Resource.fromAutoCloseable(IO(new FileInputStream(new File(path))))
          .use(inStream => {
            val bytes = new Array[Byte](chunkSizeInBytes)
            IO(Iterator.continually(inStream.read(bytes)).takeWhile(_ != -1).foreach(messageDigester.update(bytes, 0, _)))
          })
      }
      checksum <- IO(messageDigester.digest)
      mapped <- IO(checksum.map(byte => f"$byte%02x").mkString)
    } yield MatchIdInfo(mapped, Path.of(path), index)
  }
}
