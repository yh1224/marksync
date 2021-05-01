package marksync.remote.esa

import marksync.lib.Mapper
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

/**
 * Client for esa API v1
 *
 * https://docs.esa.io/posts/102
 */
class EsaApiClient(
    teamName: String,
    accessToken: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val endpoint = "https://api.esa.io/v1/teams/$teamName"
    private val headers = mapOf(
        "Content-Type" to "application/json",
        "Authorization" to "Bearer $accessToken"
    ).toHeaders()

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
    fun getMembers(): List<EsaMember> {
        val request = Request.Builder()
            .url("$endpoint/members")
            .headers(headers)
            .get()
            .build()
        System.err.println("# ${request.method} ${request.url}")
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                System.err.println("${response.code} ${response.message}: ${response.body!!.string()}")
                return listOf()
            }
            return Mapper.readJson(response.body!!.string(), EsaMembersResponse::class.java).members
        }
    }

    /**
     * Get member.
     */
    fun getMember(username: String): EsaMember? =
        getMembers().find { it.screen_name == username }

    data class EsaPostsResponse(
        val posts: List<EsaPost>
    )

    /**
     * Get posts.
     */
    fun getPosts(username: String): List<EsaPost> {
        return getMember(username)?.let { member ->
            (0..(member.posts_count - 1) / 100).flatMap { getPosts(username, it + 1) }
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
        System.err.println("# ${request.method} ${request.url}")
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                System.err.println("${response.code} ${response.message}: ${response.body!!.string()}")
                return listOf()
            }
            return Mapper.readJson(response.body!!.string(), EsaPostsResponse::class.java).posts
        }
    }

    /**
     * Get post.
     */
    fun getPost(@Suppress("UNUSED_PARAMETER") username: String, number: Int): EsaPost? {
        val request = Request.Builder()
            .url("$endpoint/posts/$number")
            .headers(headers)
            .get()
            .build()
        System.err.println("# ${request.method} ${request.url}")
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                System.err.println("${response.code} ${response.message}: ${response.body!!.string()}")
                return null
            }
            return Mapper.readJson(response.body!!.string(), EsaPost::class.java)
        }
    }

    /**
     * Save post.
     */
    fun savePost(post: EsaPost, message: String?): EsaPost? {
        val data = Mapper.getJson(
            mapOf(
                "post" to mapOf(
                    "body_md" to post.getDocumentBody(),
                    "category" to post.category,
                    "wip" to post.wip,
                    "tags" to post.tags,
                    "name" to post.getDocumentTitle(),
                    "message" to message
                )
            )
        )
        val builder = Request.Builder().headers(headers)
        if (post.number == null) {
            builder.url("$endpoint/posts")
                .post(data.toByteArray().toRequestBody("application/json".toMediaType()))
        } else {
            builder.url("$endpoint/posts/${post.number}")
                .patch(data.toByteArray().toRequestBody("application/json".toMediaType()))
        }
        val request = builder.build()
        System.err.println("# ${request.method} ${request.url}")
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                System.err.println("${response.code} ${response.message}: ${response.body!!.string()}")
                return null
            }
            return Mapper.readJson(response.body!!.string(), EsaPost::class.java)
        }
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
        System.err.println("# ${request.method} ${request.url}")
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                System.err.println("${response.code} ${response.message}: ${response.body!!.string()}")
                return null
            }
            return Mapper.readJson(response.body!!.string(), UploadPolicies::class.java)
        }
    }

    /**
     * Upload file.
     *
     * @param file File to upload
     * @return URL for uploaded file
     */
    fun uploadFile(file: File): String? {
        val contentType = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
        val policies = getUploadPolicies(file, contentType) ?: return null
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
        System.err.println("# ${request.method} ${request.url}")
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                System.err.println("${response.code} ${response.message}: ${response.body!!.string()}")
                return null
            }
            return policies.attachment.url
        }
    }
}
