package marksync.lib

import net.sourceforge.plantuml.SourceStringReader
import net.sourceforge.plantuml.code.TranscoderUtil
import org.apache.commons.codec.binary.Hex
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

object UmlUtils {
    private const val TMP_DIR_PREFIX = "marksync-uml."
    private const val TMP_FILENAME_PREFIX = ".uml:"

    /**
     * Convert UML tag to URL.
     *
     * @param umlBody UML string
     * @return Converted string
     */
    fun convertToUrl(umlBody: String): String {
        val uml = "@startuml\n$umlBody\n@enduml\n"
        val encodedUml = TranscoderUtil.getDefaultTranscoder().encode(uml)
        return "http://www.plantuml.com/plantuml/svg/$encodedUml"
    }

    /**
     * Convert UML tag to PNG image file.
     *
     * @param umlBody UML string
     * @return Converted file
     */
    fun convertToPng(umlBody: String): File {
        val dir = File.createTempFile(TMP_DIR_PREFIX, "").also {
            Files.delete(it.toPath())
            it.mkdirs()
            it.deleteOnExit()
        }
        val uml = "@startuml\n$umlBody\n@enduml\n"
        val reader = SourceStringReader(uml)
        return File(dir, pngFilename(uml)).also {
            reader.generateImage(it)
            it.deleteOnExit()
        }
    }

    /**
     * Generate png filename.
     *
     * @param uml UML string
     */
    private fun pngFilename(uml: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(uml.toByteArray())
        return TMP_FILENAME_PREFIX + Hex.encodeHexString(digest.digest()) + ".png"
    }
}
