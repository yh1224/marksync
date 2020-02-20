package marksync.esa

import java.io.File
import java.nio.file.Files

import com.softwaremill.sttp.okhttp.OkHttpSyncBackend
import com.softwaremill.sttp.{Id, Multipart, SttpBackend, Uri, multipart, multipartFile, sttp}
import marksync.{Mapper, Uploader}
import requests.RequestAuth.Bearer

case class UploadAttachment(endpoint: String, url: String)

case class UploadPolicies(attachment: UploadAttachment, form: Map[String, String])

class EsaUploader(teamName: String, username: String, accessToken: String) extends Uploader {
  implicit val backend: SttpBackend[Id, Nothing] = OkHttpSyncBackend()

  protected val ENDPOINT = s"https://api.esa.io/v1/teams/$teamName"

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
