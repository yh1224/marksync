package marksync.remote.esa

import marksync.uploader.Uploader
import java.io.File

class EsaUploader(
    teamName: String,
    accessToken: String
) : Uploader {
    private val apiClient = EsaApiClient(teamName, accessToken)

    override fun upload(file: File): String? =
        apiClient.uploadFile(file)
}
