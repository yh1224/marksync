package marksync.remote.qiita

import marksync.lib.Mapper
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client for Qiita API v2
 *
 * https://qiita.com/api/v2/docs
 */
class QiitaApiClient(
    accessToken: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val endpoint = "https://qiita.com/api/v2"
    private val headers = mapOf(
        "Content-Type" to "application/json",
        "Authorization" to "Bearer $accessToken"
    ).toHeaders()

    data class QiitaUser(
        val id: String,
        val items_count: Int
    )

    /**
     * Get user.
     */
    fun getUser(username: String): QiitaUser? {
        val request = Request.Builder()
            .url("$endpoint/users/$username")
            .headers(headers)
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            println("${response.message}: ${response.body!!.string()}")
            return null
        }
        return Mapper.readJson(response.body!!.string(), QiitaUser::class.java)
    }

    /**
     * Get items.
     */
    fun getItems(username: String): List<QiitaItem> {
        return getUser(username)?.let { user ->
            (0..((user.items_count - 1) / 100)).flatMap { getItems(username, it + 1) }
        } ?: listOf()
    }

    /**
     * Get items per page.
     */
    private fun getItems(username: String, page: Int): List<QiitaItem> {
        val request = Request.Builder()
            .url("$endpoint/items?query=user:$username&page=$page&per_page=100")
            .headers(headers)
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            println("${response.message}: ${response.body!!.string()}")
            return listOf()
        }
        return Mapper.readJson(response.body!!.string(), Array<QiitaItem>::class.java).toList()
    }

    /**
     * Get item.
     */
    fun getItem(id: String): QiitaItem? {
        val request = Request.Builder()
            .url("$endpoint/items/$id")
            .headers(headers)
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            println("${response.message}: ${response.body!!.string()}")
            return null
        }
        return Mapper.readJson(response.body!!.string(), QiitaItem::class.java)
    }

    /**
     * Save item.
     */
    fun saveItem(item: QiitaItem): QiitaItem? {
        val data = Mapper.getJson(
            mapOf(
                "body" to item.getDocumentBody(),
                "private" to item.`private`,
                "tags" to item.tags,
                "title" to item.getDocumentTitle()
            )
        )
        val builder = Request.Builder().headers(headers)
        if (item.id == null) {
            builder.url("$endpoint/items")
                .post(data.toByteArray().toRequestBody("application/json".toMediaType()))
        } else {
            builder.url("$endpoint/items/${item.id}")
                .patch(data.toByteArray().toRequestBody("application/json".toMediaType()))
        }
        val request = builder.build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            println("${response.message}: ${response.body!!.string()}")
            return null
        }
        return Mapper.readJson(response.body!!.string(), QiitaItem::class.java)
    }
}
