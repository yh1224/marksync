package marksync

import java.io.File
import java.nio.file.Files

/**
 * Document class.
 *
 * @param title Document title
 * @param body Document body
 * @param files Files map
 */
data class Document(
    val title: String,
    val body: String,
    val files: Map<String, File>
) {
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
            val bodyFile = File(dir, DOCUMENT_FILENAME)
            val body = String(Files.readAllBytes(bodyFile.toPath()))
            val bodyBuf = StringBuilder()
            val files = mutableMapOf<String, File>()
            body.split("(?<=\n)".toRegex()).forEach { line ->
                if (title != null) {
                    "\\[.*\\]\\(([^)]+)\\)".toRegex().findAll(line).forEach { m ->
                        val filename = m.groups[1]?.value!!
                        val file = File(dir, filename)
                        if (file.exists()) {
                            files[filename] = file
                        }
                    }
                    bodyBuf.append(line)
                } else if ("^#\\s".toRegex().find(line) != null) {
                    title = line.replaceFirst("^#\\s+".toRegex(), "").trim()
                }
            }
            return Document(
                title ?: "",
                bodyBuf.toString().replaceFirst("[\\r\\n]+".toRegex(), ""),
                files.toMap()
            )
        }
    }
}
