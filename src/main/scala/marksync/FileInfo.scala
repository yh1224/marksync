package marksync

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

import org.apache.commons.codec.binary.Hex

case class FileInfo(filename: String, digest: String, url: Option[String]) {
  def isIdenticalTo(file: File): Boolean = digest == FileInfo.sha1(file)
}

object FileInfo {
  def apply(filename: String, file: File, url: Option[String]): FileInfo =
    FileInfo(filename, sha1(file), url)

  def sha1(file: File): String = {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(Files.readAllBytes(file.toPath))
    Hex.encodeHexString(digest.digest)
  }
}
