package marksync.remote.qiita

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

internal class QiitaItemTest {
    private val item1 = QiitaItem(
        id = "id",
        url = "url",
        tags = listOf(QiitaItemTag("tag1")),
        private = false,
        body = "body\n",
        title = "title"
    )

    @Test
    fun getDocumentId() {
        assertEquals("id", item1.getDocumentId())
    }

    @Test
    fun getDocumentUrl() {
        assertEquals("url", item1.getDocumentUrl())
    }

    @Test
    fun getDigest() {
        assertEquals("73edb66f05c1715d8e1a90471377470ac48c963a", item1.getDigest())
    }

    @Test
    fun getDocumentTitle() {
        assertEquals("title", item1.getDocumentTitle())
    }

    @Test
    fun getDocumentBody() {
        assertEquals("body\n", item1.getDocumentBody())
    }

    @Test
    fun getDocumentBody_UML() {
        // arrange
        val item = item1.copy(
            body = """
            あいうえお
            ```uml
            a -> b
            ```
            かきくけこ
        """.trimIndent()
        )

        // act
        val result = item.getDocumentBody()

        // assert
        assertEquals(
            """
            あいうえお
            ![](.uml:9cead15a2c3d06e9f9627b9906b8d23373035287.png)
            かきくけこ
            """.trimIndent(), result
        )
        assertTrue(item.files[".uml:9cead15a2c3d06e9f9627b9906b8d23373035287.png"]?.exists() == true)
    }

    @Test
    fun saveBody() {
        // arrange
        val file = File.createTempFile("marksync-test.", "").also { it.deleteOnExit() }

        // act
        item1.saveBody(file)

        // assert
        assertEquals("# title\n\nbody\n", file.readText())
    }

    @Test
    fun isModified() {
        assertFalse(item1.isModified(item1.copy()))
        assertTrue(item1.isModified(item1.copy(tags = listOf())))
        assertTrue(item1.isModified(item1.copy(tags = listOf(QiitaItemTag("tag2")))))
        assertTrue(item1.isModified(item1.copy(tags = listOf(QiitaItemTag("tag1"), QiitaItemTag("tag2")))))
        assertTrue(item1.isModified(item1.copy(body = "body2")))
        assertTrue(item1.isModified(item1.copy(title = "title2")))
    }
}
