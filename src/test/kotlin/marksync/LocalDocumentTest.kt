package marksync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertIterableEquals
import java.io.File

internal class LocalDocumentTest {
    @org.junit.jupiter.api.Test
    fun of() {
        // arrange
        val file = File("src/test/resources/doc/test1")

        // act
        val result = LocalDocument.of(file)

        // assert
        assertEquals("テスト1", result.title)
        assertEquals("本文1\n本文2\n本文3\n\n![](image1.png)\n", result.body)
        assertIterableEquals(listOf("image1.png"), result.files.keys)
    }
}
