package marksync

import java.io.File

import com.github.difflib.DiffUtils
import com.github.difflib.patch.{ChangeDelta, DeleteDelta, InsertDelta}

import scala.jdk.CollectionConverters._

/**
 * Service specific document trait.
 */
trait ServiceDocument {
  /**
   * Get document identifier on this service.
   *
   * @return Document identifier
   */
  def getId: Option[String]

  /**
   * Get URL on this service.
   *
   * @return URL
   */
  def getUrl: Option[String]

  /**
   * Get digest.
   *
   * @param uploader Uploader
   * @return
   */
  def getDigest(uploader: Option[Uploader] = None): String

  /**
   * Get document title.
   *
   * @return document title
   */
  def getTitle: String

  /**
   * Get body.
   *
   * @return body
   */
  def getBody: String

  /**
   * Convert image tag to link primary site.
   *
   * @param fileMap filename to URL map
   * @return Converted string
   */
  def getBody(fileMap: Map[String, String]): String = {
    var body = getBody
    fileMap.foreach { case (filename, url) =>
      body = body.replaceAll(s"\\[(.*)\\]\\(\\Q$filename\\E\\)", s"[$$1]($url)")
    }
    body
  }

  /**
   * Save body data to file.
   *
   * @param file Target fileiff(
   */
  def saveBody(file: File): Unit

  /**
   * Check modified.
   *
   * @param oldDoc    Old document
   * @param uploader  Uploader
   * @param printDiff true to print differ
   */
  def isModified(oldDoc: ServiceDocument, uploader: Option[Uploader], printDiff: Boolean = false): Boolean

  /**
   * Print diff.
   *
   * @param name      Name
   * @param source    Source string
   * @param target    Target string
   * @param printDiff true to print differ
   * @return true:differ
   */
  protected def diff[A](name: String, source: A, target: A, printDiff: Boolean): Boolean = {
    val valueOf = (obj: A) => obj match {
      case s: String => s.trim().split("\r?\n").toList.asJava
      case o: Any => List(Mapper.getJson(o)).asJava
    }
    val patch = DiffUtils.diff(valueOf(source), valueOf(target))
    if (printDiff && !patch.getDeltas.isEmpty) {
      println(s"  [$name]")
      patch.getDeltas.forEach {
        case delta: InsertDelta[String] =>
          delta.getTarget.getLines.forEach(line => println(s"  +$line"))
        case delta: DeleteDelta[String] =>
          delta.getSource.getLines.forEach(line => println(s"  -$line"))
        case delta: ChangeDelta[String] =>
          delta.getSource.getLines.forEach(line => println(s"  -$line"))
          delta.getTarget.getLines.forEach(line => println(s"  +$line"))
      }
    }
    !patch.getDeltas.isEmpty
  }
}
