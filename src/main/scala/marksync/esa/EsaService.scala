package marksync.esa

import java.io.File

import com.softwaremill.sttp.okhttp.OkHttpSyncBackend
import com.softwaremill.sttp.{Id, SttpBackend, Uri, sttp}
import marksync._
import requests.RequestAuth.Bearer

case class EsaMember(screen_name: String, posts_count: Int)

case class EsaMembersResponse(members: Seq[EsaMember])

case class EsaPostResponse(posts: Seq[EsaPost])

/**
 * Esa service class.
 *
 * @param teamName    Team name
 * @param username    Username
 * @param accessToken Access Token
 */
class EsaService(teamName: String, username: String, accessToken: String) extends Service {
  implicit val backend: SttpBackend[Id, Nothing] = OkHttpSyncBackend()

  protected val META_FILENAME_PREFIX = "esa-"
  protected val META_FILENAME_POSTFIX = ".yml"
  protected val ENDPOINT = s"https://api.esa.io/v1/teams/$teamName"
  protected val HEADERS = Map(
    "Content-Type" -> "application/json"
  )

  override val META_FILENAME: String = s"$META_FILENAME_PREFIX$teamName$META_FILENAME_POSTFIX"

  protected lazy val posts: Seq[EsaPost] = {
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

  override def toServiceDocument(doc: Document, uploader: Option[Uploader]): Option[(EsaPost, Option[String])] = {
    val metaFile = new File(doc.dir, META_FILENAME)
    if (metaFile.exists()) {
      val postMeta = Option(Mapper.readYaml(metaFile, classOf[EsaPostMeta])).getOrElse(EsaPostMeta())
      if (postMeta.upload.isDefined && uploader.isDefined) {
        uploader.get.meta = postMeta.upload.get // set upload meta data
      }
      Some((EsaPost(
        number = postMeta.number,
        url = postMeta.url,
        created_at = postMeta.created_at,
        updated_at = postMeta.updated_at,
        category = postMeta.category,
        tags = postMeta.tags,
        wip = postMeta.wip,
        name = doc.title,
        body_md = doc.body,
        message = postMeta.message
      ), postMeta.digest))
    } else None
  }

  override def saveMeta(file: File, doc: ServiceDocument, uploader: Option[Uploader]): Unit = {
    val post = doc.asInstanceOf[EsaPost]

    // write qiita.yml
    Mapper.writeYaml(file, EsaPostMeta(
      number = post.number,
      url = post.url,
      created_at = post.created_at,
      updated_at = post.updated_at,
      digest = Some(post.getDigest(uploader)),
      category = post.category,
      tags = post.tags,
      wip = post.wip,
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
        "name" -> post.getTitle,
        "message" -> post.message
      )
    ))
    val updateRequest = sttp.auth.bearer(accessToken).headers(HEADERS).body(data)
    val updateResponse = post.number match {
      case Some(postId) =>
        // update
        updateRequest.patch(Uri.parse(s"$ENDPOINT/posts/$postId").get).send()
      case None =>
        // create
        updateRequest.post(Uri.parse(s"$ENDPOINT/posts").get).send()
    }
    updateResponse.body match {
      case Left(errorMessage) =>
        println(s"${updateResponse.statusText}: $errorMessage")
        None
      case Right(deserializedBody) =>
        Some(Mapper.readJson(deserializedBody, classOf[EsaPost]))
    }
  }
}
