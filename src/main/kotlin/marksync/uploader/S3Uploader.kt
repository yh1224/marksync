package marksync.uploader

import io.github.cdimascio.dotenv.Dotenv
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.internal.SystemSettingsCredentialsProvider
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ObjectCannedACL
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.utils.SystemSetting
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class S3Uploader
    (
    private val bucketName: String,
    private val prefix: String?,
    private val baseUrl: String,
    dotenv: Dotenv
) : Uploader {
    private val client: S3Client

    init {
        val clientBuilder = S3Client.builder()
            .httpClientBuilder(ApacheHttpClient.builder())
        if (dotenv["AWS_PROFILE"] != null) {
            clientBuilder.credentialsProvider(ProfileCredentialsProvider.create(dotenv["AWS_PROFILE"]))
        } else if (dotenv["AWS_ACCESS_KEY_ID"] != null && dotenv["AWS_SECRET_ACCESS_KEY"] != null) {
            clientBuilder.credentialsProvider(object : SystemSettingsCredentialsProvider() {
                override fun loadSetting(setting: SystemSetting): Optional<String> {
                    return Optional.ofNullable(dotenv[setting.environmentVariable()])
                }
            })
        }
        client = clientBuilder.build()
    }

    override fun upload(file: File): String? {
        val datePrefix = SimpleDateFormat("yyyy/MM/dd").format(file.lastModified())
        val ext =
            if (file.name.indexOf('.') >= 0) file.name.substring(file.name.lastIndexOf('.')) else ""
        val name = UUID.randomUUID().toString()
        val filePath = "$datePrefix/$name$ext"
        val objectKey = if (prefix != null) "$prefix/$filePath" else filePath
        return try {
            val request = PutObjectRequest.builder()
                .bucket(bucketName).key(objectKey)
                .acl(ObjectCannedACL.PUBLIC_READ)
                .build()
            client.putObject(request, file.toPath())
            "${baseUrl}/$objectKey"
        } catch (e: SdkClientException) {
            println("${e.message}")
            null
        }
    }
}
