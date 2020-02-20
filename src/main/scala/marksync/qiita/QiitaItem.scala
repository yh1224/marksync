package marksync.qiita

import java.io.{File, FileWriter}
import java.security.MessageDigest

import marksync.{ServiceDocument, UploadMeta, Uploader}
import net.sourceforge.plantuml.code.TranscoderUtil
import org.apache.commons.codec.binary.Hex

case class QiitaItemTag
(
  name: String,
  versions: Seq[String]
)

case class QiitaItemMeta
(
  id: Option[String] = None,
  url: Option[String] = None,
  created_at: Option[String] = None,
  updated_at: Option[String] = None,
  digest: Option[String] = None,
  tags: Seq[QiitaItemTag] = Seq(),
  `private`: Boolean = true,
  upload: Option[UploadMeta] = None
)

case class QiitaItem
(
  id: Option[String],
  url: Option[String],
  created_at: Option[String],
  updated_at: Option[String],
  tags: Seq[QiitaItemTag],
  `private`: Boolean,
  body: String,
  title: String
) extends ServiceDocument {
  override def getId: Option[String] = id

  override def getUrl: Option[String] = url

  override def getDigest(uploader: Option[Uploader] = None): String = {
    val fileMap = uploader.map(_.getFileMap).getOrElse(Map[String, String]())
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(tags.mkString(",").getBytes)
    digest.update(`private`.toString.getBytes)
    digest.update(getTitle.getBytes)
    digest.update(getBody(fileMap).getBytes)
    Hex.encodeHexString(digest.digest)
  }

  override def getTitle: String = title

  /**
   * Convert UML tag to use PlantUML server.
   *
   * @param umlBody UML string
   * @return Converted string
   */
  protected def convertUml(umlBody: String): String = {
    val uml = "@startuml\n" + umlBody + "\n@enduml\n"
    val encodedUml = TranscoderUtil.getDefaultTranscoder.encode(uml)
    s"![](http://www.plantuml.com/plantuml/svg/$encodedUml)\n"
  }

  /**
   * Convert markdown to Qiita.
   *
   * @return body
   */
  override def getBody: String = {
    val newBody = new StringBuilder()
    var fUml = false
    val umlBody = new StringBuilder()
    body.split("(?<=\n)").foreach { line =>
      // convert uml
      if (line.trim() == "```plantuml" || line.trim() == "```puml" || line.trim() == "```uml") {
        fUml = true
      } else if (fUml && line.trim() == "```") {
        newBody.append(convertUml(umlBody.toString()))
        fUml = false
      } else if (fUml) {
        umlBody.append(line)
      } else {
        newBody.append(line)
      }
    }
    newBody.toString
  }

  override def saveBody(file: File): Unit = {
    // write index.md
    val writer = new FileWriter(file)
    writer.write(s"# ${this.title}\n\n${this.body}")
    writer.close()
  }

  override def isModified(oldDoc: ServiceDocument, uploader: Option[Uploader], printDiff: Boolean = false): Boolean = {
    val oldItem = oldDoc.asInstanceOf[QiitaItem]
    val fileMap = uploader.map(_.getFileMap).getOrElse(Map[String, String]())
    Seq(
      diff("tags", oldItem.tags, this.tags, printDiff),
      diff("private", oldItem.`private`, this.`private`, printDiff),
      diff("title", oldItem.title, this.getTitle, printDiff),
      diff("body", oldItem.body, this.getBody(fileMap), printDiff)
    ).contains(true)
  }
}
