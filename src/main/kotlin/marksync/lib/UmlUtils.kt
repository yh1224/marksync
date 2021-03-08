package marksync.lib

import net.sourceforge.plantuml.SourceStringReader
import net.sourceforge.plantuml.code.TranscoderUtil
import java.io.File

object UmlUtils {
    private const val TMP_FILE_PREFIX = "marksync-uml."
    private const val TMP_FILE_SUFFIX = ".png"

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
        val uml = "@startuml\n$umlBody\n@enduml\n"
        val reader = SourceStringReader(uml)
        return File.createTempFile(TMP_FILE_PREFIX, TMP_FILE_SUFFIX).also {
            reader.generateImage(it)
        }
    }
}
