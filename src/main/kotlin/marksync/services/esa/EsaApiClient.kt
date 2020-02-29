package marksync.services.esa

import marksync.Mapper
import okhttp3.FormBody
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.nio.file.Files

class EsaApiClient(
    teamName: String,
    accessToken: String
) {
    private val endpoint = "https://api.esa.io/v1/teams/$teamName"
    private val headers = mapOf(
        "Content-Type" to "application/json",
        "Authorization" to "Bearer $accessToken"
    ).toHeaders()

    private val httpClient = OkHttpClient()

    data class EsaMember(
        val screen_name: String,
        val posts_count: Int
    )

    data class EsaMembersResponse(
        val members: List<EsaMember>
    )

    /**
     * Get members.
     */
    private fun getMembers(): List<EsaMember> {
        val request = Request.Builder()
            .url("$endpoint/members")
            .headers(headers)
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            println("${response.message}: ${response.body!!.string()}")
            return listOf()
        }
        return Mapper.readJson(response.body!!.string(), EsaMembersResponse::class.java).members
    }

    /**
     * Get member.
     */
    private fun getMember(username: String): EsaMember? =
        getMembers().find { it.screen_name == username }

    data class EsaPostResponse(
        val posts: List<EsaPost>
    )

    /**
     * Get posts.
     */
    fun getPosts(username: String): List<EsaPost> {
        return getMember(username)?.let { member ->
            (0..member.posts_count / 100).flatMap { getPosts(username, it + 1) }
        } ?: listOf()
    }

    /**
     * Get posts per page.
     */
    private fun getPosts(username: String, page: Int): List<EsaPost> {
        val request = Request.Builder()
            .url("$endpoint/posts?q=user:$username&page=$page&per_page=100")
            .headers(headers)
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            println("${response.message}: ${response.body!!.string()}")
            return listOf()
        }
        return Mapper.readJson(response.body!!.string(), EsaPostResponse::class.java).posts
    }

    /**
     * Create post.
     */
    fun createPost(data: String): EsaPost? {
        val request = Request.Builder()
            .url("$endpoint/posts")
            .headers(headers)
            .post(data.toByteArray().toRequestBody("application/json".toMediaType()))
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            println("${response.message}: ${response.body!!.string()}")
            return null
        }
        return Mapper.readJson(response.body!!.string(), EsaPost::class.java)
    }

    /**
     * Update post.
     */
    fun updatePost(number: Int, data: String): EsaPost? {
        val request = Request.Builder()
            .url("$endpoint/posts/$number")
            .headers(headers)
            .patch(data.toByteArray().toRequestBody("application/json".toMediaType()))
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            println("${response.message}: ${response.body!!.string()}")
            return null
        }
        return Mapper.readJson(response.body!!.string(), EsaPost::class.java)
    }

    data class UploadPolicies(
        val attachment: Attachment,
        val form: Map<String, String>
    ) {
        data class Attachment(
            val endpoint: String,
            val url: String
        )
    }

    /**
     * Get upload policies.
     */
    private fun getUploadPolicies(file: File, contentType: String): UploadPolicies? {
        val request = Request.Builder()
            .url("$endpoint/attachments/policies")
            .headers(headers)
            .post(
                FormBody.Builder()
                    .add("type", contentType)
                    .add("name", file.name)
                    .add("size", file.length().toString())
                    .build()
            )
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            println("${response.message}: ${response.body!!.string()}")
            return null
        }
        return Mapper.readJson(response.body!!.string(), UploadPolicies::class.java)

    }

    /**
     * Upload file.
     *
     * @param file File to upload
     * @return URL for uploaded file
     */
    fun uploadFile(file: File): String? {
        val contentType = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
        return getUploadPolicies(file, contentType)?.let { policies ->
            val bodyBuilder = MultipartBody.Builder()
            policies.form.forEach { (k, v) ->
                bodyBuilder.addFormDataPart(k, v)
            }
            bodyBuilder.addFormDataPart(
                "file",
                file.name,
                file.asRequestBody(contentType.toMediaType())
            )
            val request = Request.Builder()
                .url(policies.attachment.endpoint)
                .post(bodyBuilder.build())
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                println("${response.message}: ${response.body!!.string()}")
                return null
            }
            return policies.attachment.url
        }
    }
}
