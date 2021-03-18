package marksync.remote.esa

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

internal class EsaApiClientTest {
    // response users
    private val usersResponseBodyMock = mock<ResponseBody> {
        on { string() } doReturn """
                {
                    "members": [
                        {"screen_name":"user1","posts_count":200},
                        {"screen_name":"user2","posts_count":2}
                    ]
                }
            """.trimIndent()
    }
    private val usersResponseMock = mock<Response> {
        on { isSuccessful } doReturn true
        on { body } doReturn usersResponseBodyMock
    }
    private val usersCallMock = mock<Call> { on { execute() } doReturn usersResponseMock }

    // response posts for user1, page 1
    private val user1Posts1ResponseBodyMock = mock<ResponseBody> {
        on { string() } doReturn """
                {
                    "posts": [
                        {
                            "number": 1,
                            "url": "https://docs.esa.io/posts/1",
                            "created_at": "2015-05-09T11:54:50+09:00",
                            "updated_at": "2015-05-09T11:54:51+09:00",
                            "category": "category1",
                            "tags": ["tag1","tag2"],
                            "wip": true,
                            "body_md": "body1",
                            "name": "name1"
                        }
                    ]
                }
            """.trimIndent()
    }
    private val user1Posts1ResponseMock = mock<Response> {
        on { isSuccessful } doReturn true
        on { body } doReturn user1Posts1ResponseBodyMock
    }
    private val user1Posts1CallMock = mock<Call> { on { execute() } doReturn user1Posts1ResponseMock }

    // response posts for user1, page 2
    private val user1Posts2ResponseBodyMock = mock<ResponseBody> {
        on { string() } doReturn """
                {
                    "posts": [
                        {
                            "number": 2,
                            "url": "https://docs.esa.io/posts/2",
                            "created_at": "2015-05-10T11:54:50+09:00",
                            "updated_at": "2015-05-10T11:54:51+09:00",
                            "category": "category2",
                            "tags": ["tag3","tag4"],
                            "wip": false,
                            "body_md": "body2",
                            "name": "name2"
                        }
                    ]
                }
            """.trimIndent()
    }
    private val user1Posts2ResponseMock = mock<Response> {
        on { isSuccessful } doReturn true
        on { body } doReturn user1Posts2ResponseBodyMock
    }
    private val user1Posts2CallMock = mock<Call> { on { execute() } doReturn user1Posts2ResponseMock }

    private val httpClientMock = mock<OkHttpClient> {
        on { newCall(argThat { url.pathSegments.last() == "members" }) } doReturn usersCallMock
        on {
            newCall(argThat {
                method == "GET" &&
                        url.pathSegments.last() == "posts" &&
                        url.queryParameter("q") == "user:user1" &&
                        url.queryParameter("page") == "1"
            })
        } doReturn user1Posts1CallMock
        on {
            newCall(argThat {
                method == "GET" &&
                        url.pathSegments.last() == "posts" &&
                        url.queryParameter("q") == "user:user1" &&
                        url.queryParameter("page") == "2"
            })
        } doReturn user1Posts2CallMock
    }

    @Test
    fun getMembers() {
        // arrange
        val apiClient = EsaApiClient("team", "", httpClientMock)

        // act
        val result = apiClient.getMembers()

        // assert
        assertIterableEquals(
            listOf(
                EsaApiClient.EsaMember("user1", 200),
                EsaApiClient.EsaMember("user2", 2),
            ),
            result
        )
    }

    @Test
    fun getMember() {
        // arrange
        val apiClient = EsaApiClient("team", "", httpClientMock)

        // act
        val result = apiClient.getMember("user1")

        // assert
        assertEquals(EsaApiClient.EsaMember("user1", 200), result)
    }

    @Test
    fun getPosts() {
        // arrange
        val apiClient = EsaApiClient("team", "", httpClientMock)

        // act
        val result = apiClient.getPosts("user1")

        // assert
        assertEquals(
            listOf(
                EsaPost(
                    number = 1,
                    url = "https://docs.esa.io/posts/1",
                    created_at = "2015-05-09T11:54:50+09:00",
                    updated_at = "2015-05-09T11:54:51+09:00",
                    category = "category1",
                    tags = listOf("tag1", "tag2"),
                    wip = true,
                    body_md = "body1",
                    name = "name1",
                ),
                EsaPost(
                    number = 2,
                    url = "https://docs.esa.io/posts/2",
                    created_at = "2015-05-10T11:54:50+09:00",
                    updated_at = "2015-05-10T11:54:51+09:00",
                    category = "category2",
                    tags = listOf("tag3", "tag4"),
                    wip = false,
                    body_md = "body2",
                    name = "name2",
                ),
            ),
            result
        )
    }

    @Test
    fun getPost() {
        // arrange
        val responseBodyMock = mock<ResponseBody> {
            on { string() } doReturn """
                {
                    "number": 11,
                    "url": "https://docs.esa.io/posts/11",
                    "created_at": "2015-05-09T11:54:50+09:00",
                    "updated_at": "2015-05-09T11:54:51+09:00",
                    "category": "category11",
                    "tags": ["tag11","tag12"],
                    "wip": true,
                    "body_md": "body11",
                    "name": "name11"
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
                            url.pathSegments.takeLast(2) == listOf("posts", "11")
                })
            } doReturn callMock
        }
        val apiClient = EsaApiClient("team", "", httpClientMock)

        // act
        val result = apiClient.getPost("user1", 11)

        // assert
        assertEquals(
            EsaPost(
                number = 11,
                url = "https://docs.esa.io/posts/11",
                created_at = "2015-05-09T11:54:50+09:00",
                updated_at = "2015-05-09T11:54:51+09:00",
                category = "category11",
                tags = listOf("tag11", "tag12"),
                wip = true,
                body_md = "body11",
                name = "name11",
            ),
            result
        )
    }

    @Test
    fun savePost_new() {
        // arrange
        val responseBodyMock = mock<ResponseBody> {
            on { string() } doReturn """
                {
                    "number": 123,
                    "url": "https://docs.esa.io/posts/123",
                    "created_at": "2015-05-09T11:54:50+09:00",
                    "updated_at": "2015-05-09T11:54:51+09:00",
                    "category": "category123",
                    "tags": ["tag123"],
                    "wip": true,
                    "body_md": "body123",
                    "name": "name123"
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
                            url.pathSegments.last() == "posts" &&
                            body?.contentType()?.type == "application" &&
                            body?.contentType()?.subtype == "json" &&
                            bodyText == """
                                {"post":{"body_md":"body123","category":"category123","wip":true,"tags":["tag123"],"name":"name123","message":"message"}}
                            """.trimIndent()
                })
            } doReturn callMock
        }
        val apiClient = EsaApiClient("team", "", httpClientMock)

        // act
        val result = apiClient.savePost(
            EsaPost(
                category = "category123",
                tags = listOf("tag123"),
                wip = true,
                body_md = "body123",
                name = "name123",
            ),
            "message"
        )

        // assert
        assertEquals("123", result?.getDocumentId())
    }

    @Test
    fun savePost_modify() {
        // arrange
        val responseBodyMock = mock<ResponseBody> {
            on { string() } doReturn """
                {
                    "number": 123,
                    "url": "https://docs.esa.io/posts/123",
                    "created_at": "2015-05-09T11:54:50+09:00",
                    "updated_at": "2015-05-09T11:54:51+09:00",
                    "category": "category123",
                    "tags": ["tag123"],
                    "wip": true,
                    "body_md": "body123",
                    "name": "name123"
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
                            url.pathSegments.takeLast(2) == listOf("posts", "123") &&
                            body?.contentType()?.type == "application" &&
                            body?.contentType()?.subtype == "json" &&
                            bodyText == """
                                {"post":{"body_md":"body123","category":"category123","wip":true,"tags":["tag123"],"name":"name123","message":"message"}}
                            """.trimIndent()
                })
            } doReturn callMock
        }
        val apiClient = EsaApiClient("team", "", httpClientMock)

        // act
        val result = apiClient.savePost(
            EsaPost(
                number = 123,
                category = "category123",
                tags = listOf("tag123"),
                wip = true,
                body_md = "body123",
                name = "name123",
            ),
            "message"
        )

        // assert
        assertEquals("123", result?.getDocumentId())
    }

    @Test
    fun uploadFile() {
        // TODO
    }
}
