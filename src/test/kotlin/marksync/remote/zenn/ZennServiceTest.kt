package marksync.remote.zenn

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import marksync.LocalDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

internal class ZennServiceTest {
    private val article1 = ZennArticle(
        slug = "slug1",
        type = "type1",
        topics = listOf("topic1"),
        published = false,
        body = "body1",
        title = "title1"
    )
    private val article2 = ZennArticle(
        slug = "slug2",
        type = "type2",
        topics = listOf("topic2"),
        published = false,
        body = "body2",
        title = "title2"
    )

    @Test
    fun getDocuments() {
        // arrange
        val zennRepositoryMock = mock<ZennRepository> {
            on { getArticles() } doReturn listOf(article1, article2)
        }
        val service = ZennService(
            username = "user",
            gitDir = "",
            gitUrl = "",
            gitBranch = "main",
            gitUsername = "",
            gitPassword = "",
            zennRepository = zennRepositoryMock
        )

        // act
        val result = service.getDocuments()

        // assert
        assertEquals(article1, result["slug1"])
        assertEquals(article2, result["slug2"])
    }

    @Test
    fun getDocument() {
        // arrange
        val zennRepositoryMock = mock<ZennRepository> {
            on { getArticle("slug") } doReturn article1
        }
        val service = ZennService(
            username = "user",
            gitDir = "",
            gitUrl = "",
            gitBranch = "main",
            gitUsername = "",
            gitPassword = "",
            zennRepository = zennRepositoryMock
        )

        // act
        val result = service.getDocument("slug")

        // assert
        assertEquals(article1, result)
    }

    @Test
    fun toServiceDocument() {
        // arrange
        val service = ZennService(
            username = "user",
            gitDir = "",
            gitUrl = "",
            gitBranch = "main",
            gitUsername = "",
            gitPassword = ""
        )
        val dir = File("src/test/resources/doc/test1")
        val doc = LocalDocument.of(dir)

        // act
        val result = service.toServiceDocument(doc, dir)

        // assert
        assertEquals(null, result?.second)
        val item = result?.first
        assertEquals("テスト1", item?.title)
    }

    @Test
    fun createMeta() {
        val service = ZennService(
            username = "user",
            gitDir = "",
            gitUrl = "",
            gitBranch = "main",
            gitUsername = "",
            gitPassword = ""
        )
        val dir = File.createTempFile("marksync-test.", "").also {
            Files.delete(it.toPath())
            it.mkdirs()
            it.deleteOnExit()
        }
        service.createMeta(dir)
        assertEquals(
            """
            ---
            type: "tech"
            topics: []
            published: false
            files: []
            """.trimIndent() + "\n", File(dir, "marksync.zenn.yml").readText()
        )
    }

    @Test
    fun saveMeta() {
        // arrange
        val service = ZennService(
            username = "user",
            gitDir = "",
            gitUrl = "",
            gitBranch = "main",
            gitUsername = "",
            gitPassword = ""
        )
        val dir = File.createTempFile("marksync-test.", "").also {
            Files.delete(it.toPath())
            it.mkdirs()
            it.deleteOnExit()
        }

        // act
        service.saveMeta(article1, dir)

        // assert
        assertEquals(
            """
            ---
            slug: "slug1"
            digest: "c0d3b2d0cbcfed2146f89c4f6549e67383d36ca7"
            type: "type1"
            topics:
            - "topic1"
            published: false
            files: []
            """.trimIndent() + "\n", File(dir, "marksync.zenn.yml").readText()
        )
    }

    @Test
    fun update() {
        // arrange
        val zennRepositoryMock = mock<ZennRepository> {
            on { saveArticle(article1, "message") } doReturn article1.copy(slug = "slug")
        }
        val service = ZennService(
            username = "user",
            gitDir = "",
            gitUrl = "",
            gitBranch = "main",
            gitUsername = "",
            gitPassword = "",
            zennRepository = zennRepositoryMock
        )

        // act
        val result = service.update(article1, "message")

        // assert
        assertEquals("slug", result.getDocumentId())
    }
}
