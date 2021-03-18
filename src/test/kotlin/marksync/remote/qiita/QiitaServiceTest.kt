package marksync.remote.qiita

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import marksync.LocalDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

internal class QiitaServiceTest {
    private val item1 = QiitaItem(
        id = "id1",
        tags = listOf(QiitaItemTag("tag1")),
        private = false,
        body = "body1",
        title = "title1"
    )
    private val item2 = QiitaItem(
        id = "id2",
        tags = listOf(QiitaItemTag("tag2")),
        private = false,
        body = "body2",
        title = "title2"
    )

    @Test
    fun getDocuments() {
        // arrange
        val apiClientMock = mock<QiitaApiClient> {
            on { getItems("user") } doReturn listOf(item1, item2)
        }
        val service = QiitaService(username = "user", accessToken = "", apiClient = apiClientMock)

        // act
        val result = service.getDocuments()

        // assert
        assertEquals(item1, result["id1"])
        assertEquals(item2, result["id2"])
    }

    @Test
    fun getDocument() {
        // arrange
        val apiClientMock = mock<QiitaApiClient> {
            on { getItem("id") } doReturn item1
        }
        val service = QiitaService(username = "user", accessToken = "", apiClient = apiClientMock)

        // act
        val result = service.getDocument("id")

        // assert
        assertEquals(item1, result)
    }

    @Test
    fun toServiceDocument() {
        // arrange
        val service = QiitaService(username = "user", accessToken = "")
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
        val service = QiitaService(username = "user", accessToken = "")
        val dir = File.createTempFile("marksync-test.", "").also {
            Files.delete(it.toPath())
            it.mkdirs()
            it.deleteOnExit()
        }
        service.createMeta(dir)
        assertEquals(
            """
            ---
            tags: []
            private: true
            files: []
            """.trimIndent() + "\n", File(dir, "marksync.qiita.yml").readText()
        )
    }

    @Test
    fun saveMeta() {
        // arrange
        val service = QiitaService(username = "user", accessToken = "")
        val dir = File.createTempFile("marksync-test.", "").also {
            Files.delete(it.toPath())
            it.mkdirs()
            it.deleteOnExit()
        }

        // act
        service.saveMeta(item1, dir)

        // assert
        assertEquals(
            """
            ---
            id: "id1"
            digest: "9aa6875ffb8c8aca1d6cd74ebb31ef0e282a19dc"
            tags:
            - name: "tag1"
              versions: []
            private: false
            files: []
            """.trimIndent() + "\n", File(dir, "marksync.qiita.yml").readText()
        )
    }

    @Test
    fun update_new() {
        // arrange
        val apiClientMock = mock<QiitaApiClient> {
            on {
                saveItem(item1)
            } doReturn item1.copy(id = "new")
        }
        val service = QiitaService(username = "user", accessToken = "", apiClient = apiClientMock)

        // act
        val result = service.update(item1, "message")

        // assert
        assertEquals("new", result?.getDocumentId())
    }
}
