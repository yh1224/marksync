package marksync.remote.zenn

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

internal class ZennArticleTest {
    private val article1 = ZennArticle(
        slug = "slug",
        url = "url",
        type = "type",
        topics = listOf("topic1"),
        published = false,
        body = "body\n",
        title = "title"
    )

    @Test
    fun getDocumentId() {
        assertEquals("slug", article1.getDocumentId())
    }

    @Test
    fun getDocumentUrl() {
        assertEquals("url", article1.getDocumentUrl())
    }

    @Test
    fun getDigest() {
        assertEquals("8a42f156b7e85884fa53f63f03829da8059d8cc6", article1.getDigest())
    }

    @Test
    fun getDocumentTitle() {
        assertEquals("title", article1.getDocumentTitle())
    }

    @Test
    fun getDocumentBody() {
        assertEquals("body\n", article1.getDocumentBody())
    }

    @Test
    fun getDocumentBody_UML() {
        // arrange
        val article = article1.copy(
            body = """
            あいうえお
            ```uml
            a -> b
            ```
            かきくけこ
        """.trimIndent()
        )

        // act
        val result = article.getDocumentBody()

        // assert
        assertEquals(
            """
            あいうえお
            ![](.uml:9cead15a2c3d06e9f9627b9906b8d23373035287.png)
            かきくけこ
            """.trimIndent(), result
        )
        assertTrue(article.files[".uml:9cead15a2c3d06e9f9627b9906b8d23373035287.png"]?.exists() == true)
    }

    @Test
    fun saveBody() {
        // arrange
        val file = File.createTempFile("marksync-test.", "").also { it.deleteOnExit() }

        // act
        article1.saveBody(file)

        // assert
        assertEquals("# title\n\nbody\n", file.readText())
    }

    @Test
    fun isModified() {
        assertFalse(article1.isModified(article1.copy()))
        assertTrue(article1.isModified(article1.copy(type = "type2")))
        assertTrue(article1.isModified(article1.copy(topics = listOf())))
        assertTrue(article1.isModified(article1.copy(topics = listOf("topic2"))))
        assertTrue(article1.isModified(article1.copy(topics = listOf("topic1", "topic2"))))
        assertTrue(article1.isModified(article1.copy(body = "body2")))
        assertTrue(article1.isModified(article1.copy(title = "title2")))
    }
}
