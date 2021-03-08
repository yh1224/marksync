package marksync

import okhttp3.internal.toImmutableMap
import java.io.File
import java.nio.file.Files

/**
 * Document class.
 *
 * @param title Document title
 * @param body Document body
 */
data class Document(
    private val dir: File,
    val title: String,
    val body: String
) {
    /** files in document body */
    val files by lazy {
        val files = mutableMapOf<String, File>()
        body.split("(?<=\n)".toRegex()).forEach { line ->
            "\\[[^\\]]*\\]\\(([^)]+)\\)".toRegex().findAll(line).forEach { m ->
                val filename = m.groups[1]?.value!!.replace("%20", " ")
                val file = File(dir, filename)
                if (file.exists()) {
                    files[filename] = file
                }
            }
        }
        files.toImmutableMap()
    }

    companion object {
        const val DOCUMENT_FILENAME = "index.md"

        /**
         * Create document from directory.
         *
         * @param dir Document directory
         * @return Document
         */
        fun of(dir: File): Document {
            var title: String? = null
            val content = String(Files.readAllBytes(File(dir, DOCUMENT_FILENAME).toPath()))
            val bodyBuf = StringBuilder()
            content.split("(?<=\n)".toRegex()).forEach { line ->
                if (title != null) {
                    bodyBuf.append(line)
                } else if ("^#\\s".toRegex().find(line) != null) {
                    title = line.replaceFirst("^#\\s+".toRegex(), "").trim()
                }
            }
            return Document(
                dir,
                title ?: "",
                bodyBuf.toString().replaceFirst("[\\r\\n]+".toRegex(), "")
            )
        }
    }
}
