import java.io.{File, FileWriter}
import java.nio.file.Files
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.UUID

import $ivy.`com.fasterxml.jackson.core:jackson-core:2.10.0.pr3`
import $ivy.`com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.9`
import $ivy.`com.fasterxml.jackson.module:jackson-module-scala_2.12:2.10.0.pr3`
import $ivy.`commons-codec:commons-codec:1.13`
import $ivy.`io.github.cdimascio:java-dotenv:5.1.2`
import $ivy.`io.github.java-diff-utils:java-diff-utils:4.0`
import $ivy.`net.sourceforge.plantuml:plantuml:6703`
import $ivy.`org.slf4j:slf4j-log4j12:1.7.28`
//import $ivy.`software.amazon.awssdk:bom:2.9.7`
import $ivy.`com.softwaremill.sttp:core_2.12:1.6.8`
import $ivy.`software.amazon.awssdk:apache-client:2.9.7`
import $ivy.`software.amazon.awssdk:aws-sdk-java:2.9.7`
import com.fasterxml.jackson.annotation._
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.dataformat.yaml._
import com.fasterxml.jackson.module.scala._
import com.github.difflib.DiffUtils
import com.github.difflib.patch.{ChangeDelta, DeleteDelta, InsertDelta}
import com.softwaremill.sttp._
import io.github.cdimascio.dotenv.Dotenv
import net.sourceforge.plantuml.code.TranscoderUtil
import org.apache.commons.codec.binary.Hex
import requests.RequestAuth.Bearer
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model._

import scala.collection.JavaConverters._
import scala.collection.mutable


object Mapper {
  val jsonMapper = new ObjectMapper()
  jsonMapper.registerModule(DefaultScalaModule)
  jsonMapper.getTypeFactory.constructMapLikeType(classOf[Map[String, String]], classOf[String], classOf[String])
  jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  val yamlMapper = new ObjectMapper(new YAMLFactory())
  yamlMapper.registerModule(DefaultScalaModule)
  jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def readJson[A](str: String, aClass: Class[A]): A = jsonMapper.readValue(str, aClass)

  def readYaml[A](file: File, aClass: Class[A]): A = yamlMapper.readValue(file, aClass)

  def writeYaml[A](file: File, obj: A): Unit = yamlMapper.writeValue(file, obj)

  def getJson[A](obj: A): String = jsonMapper.writeValueAsString(obj)
}

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

/**
 * Service trait.
 */
trait Service {
  /**
   * Filename of meta file.
   */
  val META_FILENAME: String

  /**
   * Get all documents.
   *
   * @return Documents by keys
   */
  def getDocuments: Map[String, ServiceDocument]

  /**
   * Get one document.
   *
   * @param id Document identifier
   * @return Document
   */
  def getDocument(id: String): Option[ServiceDocument]

  /**
   * Get service document from Document.
   *
   * @param doc      Document
   * @param uploader Uploader
   * @return ServiceDocument object
   */
  def toServiceDocument(doc: Document, uploader: Option[Uploader]): Option[ServiceDocument]

  /**
   * Save meta data to file.
   *
   * @param file     Target file
   * @param doc      Document
   * @param uploader Uploader
   */
  def saveMeta(file: File, doc: ServiceDocument, uploader: Option[Uploader] = None): Unit

  /**
   * Update document to service.
   *
   * @param doc ServiceDocument object to update
   * @return Updated ServiceDocument object
   */
  def update(doc: ServiceDocument, uploader: Option[Uploader]): Option[ServiceDocument]

  /**
   * Sync documents to service.
   *
   * @param doc      Document
   * @param uploader Uploader
   * @param check    Set true to check only
   * @param verbose  Output verbose message
   */
  def sync(doc: Document, uploader: Option[Uploader], check: Boolean, verbose: Boolean): Unit = {
    val target = doc.dir.getPath
    this.toServiceDocument(doc, uploader).foreach { newDoc =>
      // update document
      if (newDoc.getId.isDefined) {
        // update item
        val docId = newDoc.getId.get
        val oldDocOpt = getDocument(docId)
        if (oldDocOpt.isDefined) {
          val oldDoc = oldDocOpt.get
          val docModified = newDoc.isModified(oldDoc, uploader)
          val filesModified = doc.files.exists { case (filename, file) =>
            uploader.exists(_.isModified(filename, file))
          }
          if (docModified || filesModified) {
            println(s"! $target")
            if (check) {
              if (verbose) {
                newDoc.isModified(oldDoc, uploader, printDiff = true)
              }
            } else {
              if (uploader.isDefined) {
                // upload files
                doc.files.foreach { case (filename, file) =>
                  if (uploader.get.isModified(filename, file)) {
                    val url = uploader.get.sync(filename, file)
                    if (url.isDefined) {
                      println(s"  ->uploaded. $filename to ${url.get}")
                    } else {
                      println(s"  ->upload failed. $filename")
                    }
                  }
                }
              }
              val updatedDocOpt = update(newDoc, uploader)
              updatedDocOpt match {
                case Some(item) =>
                  println(s"  ->updated. ${item.getUrl.get}")
                  saveMeta(new File(doc.dir, META_FILENAME), item, uploader)
                case None =>
                  println(s"  ->failed.")
              }
            }
          } else {
            println(s"  $target: not modified")
          }
        } else {
          println(s"? $target: ($docId) not exists.")
        }
      } else {
        // new item
        println(s"+ $target")
        if (!check) {
          val updatedItemOpt = update(newDoc, uploader)
          updatedItemOpt match {
            case Some(item) =>
              println(s"  ->created. ${item.getUrl.get}")
              saveMeta(new File(doc.dir, META_FILENAME), item, uploader)
            case None =>
              println(s"  ->failed.")
          }
        }
      }
    }
  }
}

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

case class UploadMeta(var files: Seq[FileInfo] = Seq()) {
  def getFileInfo(filename: String): Option[FileInfo] = files.find(_.filename == filename)

  /**
   * Set file info.
   *
   * @param filename file name
   * @param file     File
   * @param url      URL
   */
  def setFileInfo(filename: String, file: File, url: Option[String] = None): Unit = {
    files = files.filter(_.filename != filename) :+ FileInfo(filename, file, url)
  }
}

trait Uploader {
  /**
   * Meta data
   */
  var meta: UploadMeta = UploadMeta()

  /**
   * Check modified.
   *
   * @param filename filename
   * @param file     file
   * @return true if modified
   */
  def isModified(filename: String, file: File): Boolean =
    !meta.getFileInfo(filename).exists(_.isIdenticalTo(file))

  /**
   * Get filename to URL map.
   *
   * @return filename to URL map
   */
  @JsonIgnore
  def getFileMap: Map[String, String] =
    meta.files.filter(_.url.isDefined).map(fileInfo => (fileInfo.filename, fileInfo.url.get)).toMap

  /**
   * Sync file.
   *
   * @param filename filename (relative to document directory)
   * @param file     file to upload
   * @return URL
   */
  def sync(filename: String, file: File): Option[String] = {
    val url = upload(file)
    if (url.isDefined) {
      meta.setFileInfo(filename, file, url)
    }
    url
  }

  /**
   * Upload file.
   *
   * @param file file to upload
   * @return URL
   */
  def upload(file: File): Option[String]
}

class S3Uploader(bucketName: String, prefix: String, baseUrl: String, profile: String) extends Uploader {
  val client = S3Client.builder()
    .httpClientBuilder(ApacheHttpClient.builder())
    .credentialsProvider(ProfileCredentialsProvider.create(profile))
    .build()

  override def upload(file: File): Option[String] = {
    val datePrefix = new SimpleDateFormat("yyyy/MM/dd").format(file.lastModified())
    val ext = if (file.getName.indexOf('.') >= 0) file.getName.substring(file.getName.lastIndexOf('.')) else ""
    val name = UUID.randomUUID().toString
    val objectKey = s"$prefix/$datePrefix/$name$ext"
    val request = PutObjectRequest.builder()
      .bucket(bucketName).key(objectKey)
      .acl(ObjectCannedACL.PUBLIC_READ)
      .build()
    client.putObject(request, file.toPath)
    Some(s"$baseUrl/$objectKey")
  }
}


/*
========================================================================
Qiita (https://qiita.com)
API: https://qiita.com/api/v2/docs
========================================================================
 */

case class QiitaUser(id: String, items_count: Int)

case class QiitaItemMeta
(
  id: Option[String] = None,
  created_at: Option[String] = None,
  updated_at: Option[String] = None,
  tags: Seq[QiitaItemTag] = Seq(),
  `private`: Boolean = true,
  url: Option[String] = None,
  upload: Option[UploadMeta] = None
)

case class QiitaItemTag
(
  name: String,
  versions: Seq[String]
)

case class QiitaItem
(
  id: Option[String],
  created_at: Option[String],
  updated_at: Option[String],
  tags: Seq[QiitaItemTag],
  `private`: Boolean,
  url: Option[String],
  body: String,
  title: String
) extends ServiceDocument {
  override def getId: Option[String] = id

  override def getUrl: Option[String] = url

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

/**
 * Qiita service class.
 *
 * @param username    Username
 * @param accessToken Access Token
 */
class QiitaService(username: String, accessToken: String) extends Service {
  protected val ENDPOINT = "https://qiita.com/api/v2"
  protected val HEADERS = Seq(
    "Content-Type" -> "application/json"
  )

  override val META_FILENAME = "qiita.yml"

  protected lazy val items = {
    val userResponse = requests.get(s"$ENDPOINT/users/$username")
    assert(userResponse.is2xx)
    val user: QiitaUser = Mapper.readJson(userResponse.text(), classOf[QiitaUser])
    (0 to user.items_count / 100).flatMap { i =>
      val itemsResponse = requests.get(s"$ENDPOINT/items", params = Seq(
        "query" -> s"user:$username",
        "page" -> (i + 1).toString,
        "per_page" -> "100"
      ), auth = Bearer(accessToken))
      assert(itemsResponse.is2xx)
      Mapper.readJson(itemsResponse.text(), classOf[Array[QiitaItem]])
    }
  }

  override def getDocuments: Map[String, QiitaItem] = items.map(item => (item.id.get, item)).toMap

  override def getDocument(id: String): Option[ServiceDocument] = items.find(_.id.get == id)

  override def toServiceDocument(doc: Document, uploader: Option[Uploader]): Option[QiitaItem] = {
    val metaFile = new File(doc.dir, META_FILENAME)

    if (metaFile.exists()) {
      val itemMeta = Option(Mapper.readYaml(metaFile, classOf[QiitaItemMeta])).getOrElse(QiitaItemMeta())
      if (itemMeta.upload.isDefined && uploader.isDefined) {
        uploader.get.meta = itemMeta.upload.get // set upload meta data
      }
      Some(QiitaItem(
        id = itemMeta.id,
        created_at = itemMeta.created_at,
        updated_at = itemMeta.updated_at,
        tags = itemMeta.tags,
        `private` = itemMeta.`private`,
        url = itemMeta.url,
        body = doc.body,
        title = doc.title
      ))
    } else None
  }

  override def saveMeta(file: File, doc: ServiceDocument, uploader: Option[Uploader]): Unit = {
    val item = doc.asInstanceOf[QiitaItem]

    // write qiita.yml
    Mapper.writeYaml(file, QiitaItemMeta(
      id = item.id,
      created_at = item.created_at,
      updated_at = item.updated_at,
      tags = item.tags,
      `private` = item.`private`,
      url = item.url,
      upload = uploader.map(_.meta)
    ))
  }

  override def update(serviceDocument: ServiceDocument, uploader: Option[Uploader]): Option[ServiceDocument] = {
    val item = serviceDocument.asInstanceOf[QiitaItem]
    val fileMap = uploader.map(_.getFileMap).getOrElse(Map[String, String]())
    val data = Mapper.getJson(Map(
      "body" -> item.getBody(fileMap),
      "private" -> item.`private`,
      "tags" -> item.tags,
      "title" -> item.getTitle
    ))
    val updateResponse = item.id match {
      case Some(itemId) =>
        // update
        requests.patch(s"$ENDPOINT/items/$itemId", data = data, auth = Bearer(accessToken), headers = HEADERS)
      case None =>
        // create
        requests.post(s"$ENDPOINT/items", data = data, auth = Bearer(accessToken), headers = HEADERS)
    }
    if (!updateResponse.is2xx) {
      println(s"${updateResponse.statusMessage}: ${updateResponse.text}")
      return None
    }
    Some(Mapper.readJson(updateResponse.text(), classOf[QiitaItem]))
  }
}


/*
========================================================================
esa (https://esa.io)
API: https://docs.esa.io/posts/102
========================================================================
 */

case class UploadAttachment(endpoint: String, url: String)

case class UploadPolicies(attachment: UploadAttachment, form: Map[String, String])

class EsaUploader(teamName: String, username: String, accessToken: String) extends Uploader {
  protected val ENDPOINT = s"https://api.esa.io/v1/teams/$teamName"

  implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

  override def upload(file: File): Option[String] = {
    val contentType = Option(Files.probeContentType(file.toPath)).getOrElse("application/octet-stream")
    val policiesResponse = requests.post(s"$ENDPOINT/attachments/policies", params = Seq(
      "type" -> contentType,
      "name" -> file.getName,
      "size" -> file.length.toString
    ), auth = Bearer(accessToken))
    if (!policiesResponse.is2xx) {
      println(s"${policiesResponse.statusMessage}: ${policiesResponse.text}")
      return None
    }
    val policies = Mapper.readJson(policiesResponse.text(), classOf[UploadPolicies])
    // TODO: 411 Length Required になってしまうので sttp を使用
    // val form = policies.form.map { case (k, v) => requests.MultiItem(k, v) }.toSeq :+
    //   requests.MultiItem("file", file)
    // val updateResponse = requests.post(policies.attachment.endpoint, data = requests.MultiPart(form: _*))
    // if (updateResponse.is2xx) {
    val form: Seq[Multipart] = policies.form.map { case (k, v) => multipart(k, v) }.toSeq :+
      multipartFile("file", file)
    val request = sttp
      .multipartBody(form.head, form.tail: _*)
      .post(Uri.parse(policies.attachment.endpoint).get)
    val updateResponse = request.send()
    if (!updateResponse.isSuccess) {
      println(s"${updateResponse.statusText}: ${updateResponse.body}")
      return None
    }
    Some(policies.attachment.url)
  }
}

case class EsaMember(screen_name: String, posts_count: Int)

case class EsaMembersResponse(members: Seq[EsaMember])

case class EsaPostMeta
(
  number: Option[Int] = None,
  created_at: Option[String] = None,
  updated_at: Option[String] = None,
  category: Option[String] = None,
  tags: Seq[String] = Seq(),
  wip: Boolean = true,
  url: Option[String] = None,
  upload: Option[UploadMeta] = None
)

case class EsaPost
(
  number: Option[Int],
  created_at: Option[String],
  updated_at: Option[String],
  category: Option[String] = None,
  tags: Seq[String],
  wip: Boolean = true,
  url: Option[String],
  body_md: String,
  name: String
) extends ServiceDocument {
  override def getId: Option[String] = number.map(_.toString)

  override def getUrl: Option[String] = url

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

case class EsaPostResponse(posts: Seq[EsaPost])

/**
 * Esa service class.
 *
 * @param teamName    Team name
 * @param username    Username
 * @param accessToken Access Token
 */
class EsaService(teamName: String, username: String, accessToken: String) extends Service {
  protected val META_FILENAME_PREFIX = "esa-"
  protected val META_FILENAME_POSTFIX = ".yml"
  protected val ENDPOINT = s"https://api.esa.io/v1/teams/$teamName"
  protected val HEADERS = Seq(
    "Content-Type" -> "application/json"
  )

  override val META_FILENAME: String = s"$META_FILENAME_PREFIX$teamName$META_FILENAME_POSTFIX"

  protected lazy val posts = {
    val membersResponse = requests.get(s"$ENDPOINT/members", auth = Bearer(accessToken))
    assert(membersResponse.is2xx)
    val member = Mapper.readJson(membersResponse.text(), classOf[EsaMembersResponse]).members.find(_.screen_name == username).get
    (0 to member.posts_count / 100).flatMap { i =>
      val postsResponse = requests.get(s"$ENDPOINT/posts", params = Seq(
        "q" -> s"user:$username",
        "page" -> (i + 1).toString,
        "per_page" -> "100"
      ), auth = Bearer(accessToken))
      assert(postsResponse.is2xx)
      Mapper.readJson(postsResponse.text(), classOf[EsaPostResponse]).posts
    }
  }

  override def getDocuments: Map[String, EsaPost] = posts.map(post => (post.number.get.toString, post)).toMap

  override def getDocument(id: String): Option[ServiceDocument] = posts.find(_.number.get.toString == id)

  override def toServiceDocument(doc: Document, uploader: Option[Uploader]): Option[EsaPost] = {
    val metaFile = new File(doc.dir, META_FILENAME)
    if (metaFile.exists()) {
      val postMeta = Option(Mapper.readYaml(metaFile, classOf[EsaPostMeta])).getOrElse(EsaPostMeta())
      if (postMeta.upload.isDefined && uploader.isDefined) {
        uploader.get.meta = postMeta.upload.get // set upload meta data
      }
      Some(EsaPost(
        number = postMeta.number,
        created_at = postMeta.created_at,
        updated_at = postMeta.updated_at,
        category = postMeta.category,
        tags = postMeta.tags,
        wip = postMeta.wip,
        url = postMeta.url,
        body_md = doc.body,
        name = doc.title
      ))
    } else None
  }

  override def saveMeta(file: File, doc: ServiceDocument, uploader: Option[Uploader]): Unit = {
    val post = doc.asInstanceOf[EsaPost]

    // write qiita.yml
    Mapper.writeYaml(file, EsaPostMeta(
      number = post.number,
      created_at = post.created_at,
      updated_at = post.updated_at,
      category = post.category,
      tags = post.tags,
      wip = post.wip,
      url = post.url,
      upload = uploader.map(_.meta)
    ))
  }

  override def update(serviceDocument: ServiceDocument, uploader: Option[Uploader]): Option[ServiceDocument] = {
    val post = serviceDocument.asInstanceOf[EsaPost]
    val fileMap = uploader.map(_.getFileMap).getOrElse(Map[String, String]())
    val data = Mapper.getJson(Map(
      "post" -> Map(
        "body_md" -> post.getBody(fileMap),
        "category" -> post.category,
        "wip" -> post.wip,
        "tags" -> post.tags,
        "name" -> post.getTitle
      )
    ))
    val updateResponse = post.number match {
      case Some(postId) =>
        // update
        requests.patch(s"$ENDPOINT/posts/$postId", data = data, auth = Bearer(accessToken), headers = HEADERS)
      case None =>
        // create
        requests.post(s"$ENDPOINT/posts", data = data, auth = Bearer(accessToken), headers = HEADERS)
    }
    if (!updateResponse.is2xx) {
      println(s"${updateResponse.statusMessage}: ${updateResponse.text}")
      return None
    }
    Some(Mapper.readJson(updateResponse.text(), classOf[EsaPost]))
  }
}


/*
========================================================================
 */

/**
 * List all files recursively under the directory.
 *
 * @param dir Directory
 * @return Files
 */
def listFiles(dir: File): Array[File] = {
  val these = dir.listFiles
  these ++ these.filter(_.isDirectory).flatMap(listFiles)
}

/**
 * Fetch all documents from service.
 *
 * @param outDir  Output directory
 * @param service Service object
 */
def fetchAll(outDir: File, service: Service): Unit = {
  service.getDocuments.foreach { case (docId: String, doc: ServiceDocument) =>
    println(s"$docId ${doc.getUrl.get}")
    val dir = new File(outDir, docId)
    println(s"  -> ${dir.getAbsolutePath}")
    dir.mkdirs()
    service.saveMeta(new File(dir, service.META_FILENAME), doc)
    doc.saveBody(new File(dir, Document.DOCUMENT_FILENAME))
  }
}

/**
 * Sync all documents under the directory.
 *
 * @param fromDir  Input directory path
 * @param service  Service object
 * @param uploader Uploader
 * @param check    Set true to check only
 * @param verbose  Output verbose message
 */
def updateAll(fromDir: File, service: Service, uploader: Option[Uploader], check: Boolean, verbose: Boolean): Unit = {
  listFiles(fromDir)
    .filter(_.getName == "index.md")
    .map(_.getParentFile)
    .foreach { dir =>
      val doc = Document(dir)
      service.sync(doc, uploader, check, verbose)
    }
}

/**
 * Get service from env.
 *
 * @param dotEnv Environment
 * @return Service
 */
def getService(dotEnv: Dotenv): Service = {
  dotEnv.get("SERVICE") match {
    case "qiita" =>
      new QiitaService(dotEnv.get("QIITA_USERNAME"), dotEnv.get("QIITA_ACCESS_TOKEN"))
    case "esa" =>
      new EsaService(dotEnv.get("ESA_TEAM"), dotEnv.get("ESA_USERNAME"), dotEnv.get("ESA_ACCESS_TOKEN"))
  }
}

/**
 * Get uploader from env.
 *
 * @param dotEnv Environment
 * @return Uploader
 */
def getUploader(dotEnv: Dotenv): Option[Uploader] = {
  Option(dotEnv.get("UPLOADER")) match {
    case Some("s3") =>
      Some(new S3Uploader(
        dotEnv.get("S3_BUCKET_NAME"),
        dotEnv.get("S3_PREFIX"),
        dotEnv.get("S3_BASE_URL"),
        dotEnv.get("AWS_PROFILE")))
    case None =>
      dotEnv.get("SERVICE") match {
        case "esa" => Some(new EsaUploader(
          dotEnv.get("ESA_TEAM"),
          dotEnv.get("ESA_USERNAME"),
          dotEnv.get("ESA_ACCESS_TOKEN")))
        case _ => None
      }
  }
}

@main
def fetch(output: String, env: String = ".env"): Unit = {
  val dotEnv = Dotenv.configure().filename(env).load()
  fetchAll(new File(output), getService(dotEnv))
}

@main
def check(target: String, env: String = ".env", verbose: Boolean = false): Unit = {
  val dotEnv = Dotenv.configure().filename(env).load()
  updateAll(new File(target), getService(dotEnv), getUploader(dotEnv), check = true, verbose = verbose)
}

@main
def update(target: String, env: String = ".env", verbose: Boolean = false): Unit = {
  val dotEnv = Dotenv.configure().filename(env).load()
  updateAll(new File(target), getService(dotEnv), getUploader(dotEnv), check = false, verbose = verbose)
}
