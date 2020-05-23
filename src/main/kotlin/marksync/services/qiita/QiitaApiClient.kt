package marksync.services.qiita

import marksync.Mapper
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class QiitaApiClient(
    accessToken: String
) {
    private val endpoint = "https://qiita.com/api/v2"
    private val headers = mapOf(
        "Content-Type" to "application/json",
        "Authorization" to "Bearer $accessToken"
    ).toHeaders()

    private val httpClient = OkHttpClient()

    data class QiitaUser(
        val id: String,
        val items_count: Int
    )

    /**
     * Get user.
     */
    private fun getUser(username: String): QiitaUser? {
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
            (0..user.items_count / 100).flatMap { getItems(username, it + 1) }
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
     * Create item.
     */
    fun createItem(data: String): QiitaItem? {
        val updateRequest = Request.Builder()
            .url("$endpoint/items")
            .headers(headers)
            .post(data.toByteArray().toRequestBody("application/json".toMediaType()))
            .build()
        val response = httpClient.newCall(updateRequest).execute()
        if (!response.isSuccessful) {
            println("${response.message}: ${response.body!!.string()}")
            return null
        }
        return Mapper.readJson(response.body!!.string(), QiitaItem::class.java)
    }

    /**
     * Update item.
     */
    fun updateItem(id: String, data: String): QiitaItem? {
        val updateRequest = Request.Builder()
            .url("$endpoint/items/$id")
            .headers(headers)
            .patch(data.toByteArray().toRequestBody("application/json".toMediaType()))
            .build()
        val response = httpClient.newCall(updateRequest).execute()
        if (!response.isSuccessful) {
            println("${response.message}: ${response.body!!.string()}")
            return null
        }
        return Mapper.readJson(response.body!!.string(), QiitaItem::class.java)
    }
}
