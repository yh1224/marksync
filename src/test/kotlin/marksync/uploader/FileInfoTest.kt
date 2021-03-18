package marksync.uploader

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

internal class FileInfoTest {
    @Test
    fun sha1() {
        // arrange
        val file = File("src/test/resources/doc/test1/image1.png")

        // act
        val result = FileInfo.sha1(file)

        // assert
        assertEquals("9361921e06fd7d90b7215bee2fb25f8359f2f5e6", result)
    }

    @Test
    fun isIdenticalToTrue() {
        // arrange
        val fileInfo = FileInfo("image1.png", "9361921e06fd7d90b7215bee2fb25f8359f2f5e6", null)

        // act
        val file = File("src/test/resources/doc/test1/image1.png")

        // assert
        assertTrue(fileInfo.isIdenticalTo(file))
    }

    @Test
    fun isIdenticalToFalse() {
        // arrange
        val fileInfo = FileInfo("image1.png", "9361921e06fd7d90b7215bee2fb25f8359f2f5e5", null)

        // act
        val file = File("src/test/resources/doc/test1/image1.png")

        // assert
        assertFalse(fileInfo.isIdenticalTo(file))
    }
}
