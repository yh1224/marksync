package marksync.remote.qiita

import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test

internal class QiitaApiClientTest {
    // response user
    private val user1ResponseBodyMock = mock<ResponseBody> {
        on { string() } doReturn """
            {
                "id": "user1",
                "items_count": 200
            }
            """.trimIndent()
    }
    private val user1ResponseMock = mock<Response> {
        on { isSuccessful } doReturn true
        on { body } doReturn user1ResponseBodyMock
    }
    private val userCallMock = mock<Call> { on { execute() } doReturn user1ResponseMock }

    // response posts for user1, page 1
    private val items1ResponseBodyMock = mock<ResponseBody> {
        on { string() } doReturn """
            [
                {
                    "id": "1",
                    "url": "https://qiita.com/Qiita/items/1",
                    "created_at": "2000-01-01T00:00:00+00:00",
                    "updated_at": "2000-01-01T00:00:00+00:00",
                    "tags": [{"name":"tag1","versions":[]},{"name":"tag2","versions":[]}],
                    "private": false,
                    "body": "body1",
                    "title": "title1"
                }
            ]
            """.trimIndent()
    }
    private val items1ResponseMock = mock<Response> {
        on { isSuccessful } doReturn true
        on { body } doReturn items1ResponseBodyMock
    }
    private val items1CallMock = mock<Call> { on { execute() } doReturn items1ResponseMock }

    // response posts for user1, page 1
    private val items2ResponseBodyMock = mock<ResponseBody> {
        on { string() } doReturn """
            [
                {
                    "id": "2",
                    "url": "https://qiita.com/Qiita/items/2",
                    "created_at": "2000-01-02T00:00:00+00:00",
                    "updated_at": "2000-01-02T00:00:00+00:00",
                    "tags": [{"name":"tag3","versions":[]},{"name":"tag4","versions":[]}],
                    "private": true,
                    "body": "body2",
                    "title": "title2"
                }
            ]
            """.trimIndent()
    }
    private val items2ResponseMock = mock<Response> {
        on { isSuccessful } doReturn true
        on { body } doReturn items2ResponseBodyMock
    }
    private val items2CallMock = mock<Call> { on { execute() } doReturn items2ResponseMock }

    private val httpClientMock = mock<OkHttpClient> {
        on {
            newCall(argThat {
                method == "GET" &&
                        url.pathSegments.takeLast(2) == listOf("users", "user1")
            })
        } doReturn userCallMock
        on {
            newCall(argThat {
                method == "GET" &&
                        url.pathSegments.last() == "items" &&
                        url.queryParameter("query") == "user:user1" &&
                        url.queryParameter("page") == "1"
            })
        } doReturn items1CallMock
        on {
            newCall(argThat {
                method == "GET" &&
                        url.pathSegments.last() == "items" &&
                        url.queryParameter("query") == "user:user1" &&
                        url.queryParameter("page") == "2"
            })
        } doReturn items2CallMock
    }

    @Test
    fun getUser() {
        // arrange
        val apiClient = QiitaApiClient("", httpClientMock)

        // act
        val result = apiClient.getUser("user1")

        // assert
        assertEquals(QiitaApiClient.QiitaUser(id = "user1", items_count = 200), result)
    }

    @Test
    fun getItems() {
        // arrange
        val apiClient = QiitaApiClient("", httpClientMock)

        // act
        val result = apiClient.getItems("user1")

        // assert
        assertIterableEquals(
            listOf(
                QiitaItem(
                    id = "1",
                    url = "https://qiita.com/Qiita/items/1",
                    created_at = "2000-01-01T00:00:00+00:00",
                    updated_at = "2000-01-01T00:00:00+00:00",
                    tags = listOf(QiitaItemTag("tag1"), QiitaItemTag("tag2")),
                    private = false,
                    body = "body1",
                    title = "title1",
                ),
                QiitaItem(
                    id = "2",
                    url = "https://qiita.com/Qiita/items/2",
                    created_at = "2000-01-02T00:00:00+00:00",
                    updated_at = "2000-01-02T00:00:00+00:00",
                    tags = listOf(QiitaItemTag("tag3"), QiitaItemTag("tag4")),
                    private = true,
                    body = "body2",
                    title = "title2",
                ),
            ),
            result
        )
    }

    @Test
    fun getItem() {
        // arrange
        val responseBodyMock = mock<ResponseBody> {
            on { string() } doReturn """
                {
                    "id": "11",
                    "url": "https://qiita.com/Qiita/items/11",
                    "created_at": "2000-01-11T00:00:00+00:00",
                    "updated_at": "2000-01-11T00:00:00+00:00",
                    "tags": [{"name":"tag11","versions":[]},{"name":"tag12","versions":[]}],
                    "private": false,
                    "body": "body11",
                    "title": "title11"
                }
                """.trimIndent()
        }
        val responseMock = mock<Response> {
            on { isSuccessful } doReturn true
            on { body } doReturn responseBodyMock
        }
        val callMock = mock<Call> { on { execute() } doReturn responseMock }
        val httpClientMock = mock<OkHttpClient> {
            on {
                newCall(argThat {
                    method == "GET" &&
                            url.pathSegments.takeLast(2) == listOf("items", "11")
                })
            } doReturn callMock
        }
        val apiClient = QiitaApiClient("", httpClientMock)

        // act
        val result = apiClient.getItem("11")

        // assert
        assertEquals(
            QiitaItem(
                id = "11",
                url = "https://qiita.com/Qiita/items/11",
                created_at = "2000-01-11T00:00:00+00:00",
                updated_at = "2000-01-11T00:00:00+00:00",
                tags = listOf(QiitaItemTag("tag11"), QiitaItemTag("tag12")),
                private = false,
                body = "body11",
                title = "title11",
            ),
            result
        )
    }

    @Test
    fun saveItem_new() {
        // arrange
        val responseBodyMock = mock<ResponseBody> {
            on { string() } doReturn """
                {
                    "id": "123",
                    "url": "https://qiita.com/Qiita/items/123",
                    "created_at": "2000-01-12T00:00:00+00:00",
                    "updated_at": "2000-01-12T00:00:00+00:00",
                    "tags": [{"name":"tag123","versions":[]}],
                    "private": false,
                    "body": "body123",
                    "title": "title123"
                }
            """.trimIndent()
        }
        val responseMock = mock<Response> {
            on { isSuccessful } doReturn true
            on { body } doReturn responseBodyMock
        }
        val callMock = mock<Call> { on { execute() } doReturn responseMock }
        val httpClientMock = mock<OkHttpClient> {
            on {
                newCall(argThat {
                    val bodyText = Buffer().also { body?.writeTo(it) }.readUtf8()
                    method == "POST" &&
                            url.pathSegments.last() == "items" &&
                            body?.contentType()?.type == "application" &&
                            body?.contentType()?.subtype == "json" &&
                            bodyText == """
                                {"body":"body123","private":false,"tags":[{"name":"tag123","versions":[]}],"title":"title123"}
                            """.trimIndent()
                })
            } doReturn callMock
        }
        val apiClient = QiitaApiClient("", httpClientMock)

        // act
        val result = apiClient.saveItem(
            QiitaItem(
                tags = listOf(QiitaItemTag("tag123")),
                private = false,
                body = "body123",
                title = "title123",
            )
        )

        // assert
        assertEquals("123", result?.getDocumentId())
    }

    @Test
    fun saveItem_modify() {
        // arrange
        val responseBodyMock = mock<ResponseBody> {
            on { string() } doReturn """
                {
                    "id": "456",
                    "url": "https://qiita.com/Qiita/items/456",
                    "created_at": "2000-01-12T00:00:00+00:00",
                    "updated_at": "2000-01-12T00:00:00+00:00",
                    "tags": [{"name":"tag456","versions":[]}],
                    "private": false,
                    "body": "body456",
                    "title": "title456"
                }
            """.trimIndent()
        }
        val responseMock = mock<Response> {
            on { isSuccessful } doReturn true
            on { body } doReturn responseBodyMock
        }
        val callMock = mock<Call> { on { execute() } doReturn responseMock }
        val httpClientMock = mock<OkHttpClient> {
            on {
                newCall(argThat {
                    val bodyText = Buffer().also { body?.writeTo(it) }.readUtf8()
                    method == "PATCH" &&
                            url.pathSegments.takeLast(2) == listOf("items", "456") &&
                            body?.contentType()?.type == "application" &&
                            body?.contentType()?.subtype == "json" &&
                            bodyText == """
                                {"body":"body456","private":false,"tags":[{"name":"tag456","versions":[]}],"title":"title456"}
                            """.trimIndent()
                })
            } doReturn callMock
        }
        val apiClient = QiitaApiClient("", httpClientMock)

        // act
        val result = apiClient.saveItem(
            QiitaItem(
                id = "456",
                tags = listOf(QiitaItemTag("tag456")),
                private = false,
                body = "body456",
                title = "title456",
            )
        )

        // assert
        assertEquals("456", result?.getDocumentId())
    }
}
