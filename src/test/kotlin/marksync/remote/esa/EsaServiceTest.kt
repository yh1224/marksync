package marksync.remote.esa

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import marksync.LocalDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

internal class EsaServiceTest {
    private val post1 = EsaPost(
        number = 1,
        category = "category1",
        tags = listOf("tag1"),
        wip = false,
        body_md = "body1",
        name = "title1"
    )
    private val post2 = EsaPost(
        number = 2,
        category = "category2",
        tags = listOf("tag2"),
        wip = false,
        body_md = "body2",
        name = "title2"
    )

    @Test
    fun getDocuments() {
        // arrange
        val apiClientMock = mock<EsaApiClient> {
            on { getPosts("user") } doReturn listOf(post1, post2)
        }
        val service = EsaService(teamName = "team", username = "user", accessToken = "", apiClient = apiClientMock)

        // act
        val result = service.getDocuments()

        // assert
        assertEquals(post1, result["1"])
        assertEquals(post2, result["2"])
    }

    @Test
    fun getDocument() {
        // arrange
        val apiClientMock = mock<EsaApiClient> {
            on { getPost("user", 123) } doReturn post1
        }
        val service = EsaService(teamName = "team", username = "user", accessToken = "", apiClient = apiClientMock)

        // act
        val result = service.getDocument("123")

        // assert
        assertEquals(post1, result)
    }

    @Test
    fun toServiceDocument() {
        // arrange
        val service = EsaService(teamName = "team", username = "user", accessToken = "")
        val dir = File("src/test/resources/doc/test1")
        val doc = LocalDocument.of(dir)

        // act
        val result = service.toServiceDocument(doc, dir)

        // assert
        assertEquals(null, result?.second)
        val item = result?.first
        assertEquals("テスト1", item?.name)
    }

    @Test
    fun createMeta() {
        val service = EsaService(teamName = "team", username = "user", accessToken = "")
        val dir = File.createTempFile("marksync-test.", "").also {
            Files.delete(it.toPath())
            it.mkdirs()
            it.deleteOnExit()
        }
        service.createMeta(dir)
        assertEquals(
            """
            ---
            category: ""
            tags: []
            wip: true
            files: []
            """.trimIndent() + "\n", File(dir, "marksync.esa.yml").readText()
        )
    }

    @Test
    fun saveMeta() {
        // arrange
        val service = EsaService(teamName = "team", username = "user", accessToken = "")
        val dir = File.createTempFile("marksync-test.", "").also {
            Files.delete(it.toPath())
            it.mkdirs()
            it.deleteOnExit()
        }

        // act
        service.saveMeta(post1, dir)

        // assert
        assertEquals(
            """
            ---
            number: 1
            digest: "59a685e19c0d23484be9df90d6b970beb2648f7c"
            category: "category1"
            tags:
            - "tag1"
            wip: false
            files: []
            """.trimIndent() + "\n", File(dir, "marksync.esa.yml").readText()
        )
    }

    @Test
    fun update() {
        // arrange
        val apiClientMock = mock<EsaApiClient> {
            on {
                savePost(post1, "message")
            } doReturn post1.copy(number = 456)
        }
        val service = EsaService(teamName = "team", username = "user", accessToken = "", apiClient = apiClientMock)

        // act
        val result = service.update(post1, "message")

        // assert
        assertEquals("456", result?.getDocumentId())
    }
}
