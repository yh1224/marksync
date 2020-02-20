package marksync.qiita

import java.io.File

import com.softwaremill.sttp.okhttp.OkHttpSyncBackend
import com.softwaremill.sttp.{Id, SttpBackend, Uri, sttp}
import marksync._
import requests.RequestAuth.Bearer

case class QiitaUser(id: String, items_count: Int)

/**
 * Qiita service class.
 *
 * @param username    Username
 * @param accessToken Access Token
 */
class QiitaService(username: String, accessToken: String) extends Service {
  implicit val backend: SttpBackend[Id, Nothing] = OkHttpSyncBackend()

  protected val ENDPOINT = "https://qiita.com/api/v2"
  protected val HEADERS = Map(
    "Content-Type" -> "application/json"
  )

  override val META_FILENAME = "qiita.yml"

  protected lazy val items: Seq[QiitaItem] = {
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

  override def toServiceDocument(doc: Document, uploader: Option[Uploader]): Option[(QiitaItem, Option[String])] = {
    val metaFile = new File(doc.dir, META_FILENAME)

    if (metaFile.exists()) {
      val itemMeta = Option(Mapper.readYaml(metaFile, classOf[QiitaItemMeta])).getOrElse(QiitaItemMeta())
      if (itemMeta.upload.isDefined && uploader.isDefined) {
        uploader.get.meta = itemMeta.upload.get // set upload meta data
      }
      Some((QiitaItem(
        id = itemMeta.id,
        url = itemMeta.url,
        created_at = itemMeta.created_at,
        updated_at = itemMeta.updated_at,
        tags = itemMeta.tags,
        `private` = itemMeta.`private`,
        body = doc.body,
        title = doc.title
      ), itemMeta.digest))
    } else None
  }

  override def saveMeta(file: File, doc: ServiceDocument, uploader: Option[Uploader]): Unit = {
    val item = doc.asInstanceOf[QiitaItem]

    // write qiita.yml
    Mapper.writeYaml(file, QiitaItemMeta(
      id = item.id,
      url = item.url,
      created_at = item.created_at,
      updated_at = item.updated_at,
      digest = Some(item.getDigest(uploader)),
      tags = item.tags,
      `private` = item.`private`,
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
    val updateRequest = sttp.auth.bearer(accessToken).headers(HEADERS).body(data)
    val updateResponse = item.id match {
      case Some(itemId) =>
        // update
        updateRequest.patch(Uri.parse(s"$ENDPOINT/items/$itemId").get).send()
      case None =>
        // create
        updateRequest.post(Uri.parse(s"$ENDPOINT/items").get).send()
    }
    updateResponse.body match {
      case Left(errorMessage) =>
        println(s"${updateResponse.statusText}: $errorMessage")
        None
      case Right(deserializedBody) =>
        Some(Mapper.readJson(deserializedBody, classOf[QiitaItem]))
    }
  }
}
