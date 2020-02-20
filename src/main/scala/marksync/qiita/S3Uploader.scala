package marksync.qiita

import java.io.File
import java.text.SimpleDateFormat
import java.util.UUID

import marksync.Uploader
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{ObjectCannedACL, PutObjectRequest}

class S3Uploader(bucketName: String, prefix: String, baseUrl: String, profile: String) extends Uploader {
  val client: S3Client = S3Client.builder()
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
