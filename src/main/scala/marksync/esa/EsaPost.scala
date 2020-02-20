package marksync.esa

import java.io.{File, FileWriter}
import java.security.MessageDigest

import marksync.{ServiceDocument, UploadMeta, Uploader}
import org.apache.commons.codec.binary.Hex

case class EsaPostMeta
(
  number: Option[Int] = None,
  url: Option[String] = None,
  created_at: Option[String] = None,
  updated_at: Option[String] = None,
  digest: Option[String] = None,
  category: Option[String] = None,
  tags: Seq[String] = Seq(),
  wip: Boolean = true,
  upload: Option[UploadMeta] = None,
  message: Option[String] = None
)

case class EsaPost
(
  number: Option[Int],
  url: Option[String],
  created_at: Option[String],
  updated_at: Option[String],
  category: Option[String] = None,
  tags: Seq[String],
  wip: Boolean = true,
  body_md: String,
  name: String,
  message: Option[String] = None
) extends ServiceDocument {
  override def getId: Option[String] = number.map(_.toString)

  override def getUrl: Option[String] = url

  override def getDigest(uploader: Option[Uploader] = None): String = {
    val fileMap = uploader.map(_.getFileMap).getOrElse(Map[String, String]())
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(category.getOrElse("").getBytes)
    digest.update(tags.mkString(",").getBytes)
    digest.update(wip.toString.getBytes)
    digest.update(getTitle.getBytes)
    digest.update(getBody(fileMap).getBytes)
    Hex.encodeHexString(digest.digest)
  }

  override def getTitle: String = {
    name.replaceAll("/", "")
  }

  override def getBody: String = {
    val newBody = new StringBuilder()
    body_md.split("(?<=\n)").foreach { line =>
      if (line.trim() == "```plantuml") {
        newBody.append(line.replaceAll("^```plantuml", "```uml"))
      } else if (line.trim() == "```puml") {
        newBody.append(line.replaceAll("^```puml", "```uml"))
      } else {
        newBody.append(line)
      }
    }
    newBody.toString
  }

  override def saveBody(file: File): Unit = {
    // write index.md
    val writer = new FileWriter(file)
    writer.write(s"# ${this.name}\n\n${this.body_md}")
    writer.close()
  }

  override def isModified(oldDoc: ServiceDocument, uploader: Option[Uploader], printDiff: Boolean = false): Boolean = {
    val oldPost = oldDoc.asInstanceOf[EsaPost]
    val fileMap = uploader.map(_.getFileMap).getOrElse(Map[String, String]())
    Seq(
      diff("category", oldPost.category, this.category, printDiff),
      diff("tags", oldPost.tags, this.tags, printDiff),
      diff("wip", oldPost.wip, this.wip, printDiff),
      diff("title", oldPost.name, this.getTitle, printDiff),
      diff("body", oldPost.body_md, this.getBody(fileMap), printDiff)
    ).contains(true)
  }
}
