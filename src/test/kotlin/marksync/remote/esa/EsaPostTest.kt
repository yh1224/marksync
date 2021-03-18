package marksync.remote.esa

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

internal class EsaPostTest {
    private val post1 = EsaPost(
        number = 123,
        url = "url",
        category = "category",
        tags = listOf("tag1"),
        wip = false,
        body_md = "body\n",
        name = "name"
    )

    @Test
    fun getDocumentId() {
        assertEquals("123", post1.getDocumentId())
    }

    @Test
    fun getDocumentUrl() {
        assertEquals("url", post1.getDocumentUrl())
    }

    @Test
    fun getDigest() {
        assertEquals("0bcbb85112d686e0e8ab6af5024eae6bd1c1bd57", post1.getDigest())
    }

    @Test
    fun getDocumentTitle() {
        assertEquals("name", post1.getDocumentTitle())
    }

    @Test
    fun getDocumentBody() {
        // arrange
        val post = post1.copy(
            body_md = """
            あいうえお
            ```plantuml
            a -> b
            ```
            かきくけこ
        """.trimIndent()
        )

        // act
        val result = post.getDocumentBody()

        // assert
        assertEquals(
            """
            あいうえお
            ```uml
            a -> b
            ```
            かきくけこ
            """.trimIndent(), result
        )
    }

    @Test
    fun saveBody() {
        // arrange
        val file = File.createTempFile("marksync-test.", "").also { it.deleteOnExit() }

        // act
        post1.saveBody(file)

        // assert
        assertEquals("# name\n\nbody\n", file.readText())
    }

    @Test
    fun isModified() {
        assertFalse(post1.isModified(post1.copy()))
        assertTrue(post1.isModified(post1.copy(category = "category2")))
        assertTrue(post1.isModified(post1.copy(tags = listOf())))
        assertTrue(post1.isModified(post1.copy(tags = listOf("tag2"))))
        assertTrue(post1.isModified(post1.copy(tags = listOf("tag1", "tag2"))))
        assertTrue(post1.isModified(post1.copy(body_md = "body2")))
        assertTrue(post1.isModified(post1.copy(name = "name2")))
    }
}
