package marksync

import java.io.File
import java.nio.file.Files

import scala.collection.mutable

/**
 * Document class.
 *
 * @param dir   Document directory
 * @param title Document title
 * @param body  Document body
 */
case class Document(dir: File, title: String, body: String, files: Map[String, File])

/**
 * Document class.
 */
object Document {
  val DOCUMENT_FILENAME = "index.md"

  /**
   * Create document from directory.
   *
   * @param dir Document directory
   * @return Document
   */
  def apply(dir: File): Document = {
    var titleOpt: Option[String] = None
    val bodyFile = new File(dir, DOCUMENT_FILENAME)
    val body = new String(Files.readAllBytes(bodyFile.toPath))
    val bodyBuf = new StringBuilder()
    val files = mutable.Map[String, File]()
    body.split("(?<=\n)").foreach { line =>
      if (titleOpt.isDefined) {
        "\\[.*\\]\\(([^)]+)\\)".r.findAllIn(line).matchData foreach { m =>
          val filename = m.group(1)
          val file = new File(dir, filename)
          if (file.exists()) {
            files(filename) = file
          }
        }
        bodyBuf.append(line)
      } else if ("^#\\s".r.findFirstIn(line).isDefined) {
        titleOpt = Some(line.replaceFirst("^#\\s+", "").trim)
      }
    }
    Document(dir,
      titleOpt.getOrElse(""), bodyBuf.toString().replaceFirst("[\\r\\n]+", ""),
      files.toMap)
  }
}
