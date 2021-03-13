package marksync.lib

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files

internal class UmlUtilsTest {
    @org.junit.jupiter.api.Test
    fun convertToUrl() {
        val result = UmlUtils.convertToUrl("a -> b: foo\na <-- b")
        assertEquals("http://www.plantuml.com/plantuml/svg/IrJGjLD8ib98oy_dIbImqTLLI080", result)
    }

    @org.junit.jupiter.api.Test
    fun convertToTempFile() {
        val result = UmlUtils.convertToPng("a -> b: foo\na <-- b")
        assertTrue(result.isFile)
        assertEquals(".uml:fa887bea143f42006c9b425fd3d210be4293851b.png", result.name)
        Files.delete(result.toPath())
    }
}
