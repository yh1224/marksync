package marksync

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.toImmutableMap
import java.io.File
import java.nio.file.Files

/**
 * Local document class.
 *
 * @param title Document title
 * @param body Document body
 */
data class LocalDocument(
    private val dir: File,
    val title: String,
    val body: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    /** files in document body */
    val files by lazy {
        val files = mutableMapOf<String, File>()
        body.split("(?<=\n)".toRegex()).forEach { line ->
            "\\[[^\\]]*\\]\\(([^)]+)\\)".toRegex().findAll(line).forEach { m ->
                val filename = m.groups[1]?.value!!.replace("%20", " ")
                if (filename.matches("[a-z]+://.*".toRegex())) {
                    /*
                    if (!checkURL(filename)) {
                        System.err.println("warning: not accessible publicly: $filename")
                    }
                     */
                } else if (!filename.matches("#.*".toRegex())) {
                    val file = File(dir, filename)
                    if (file.exists() && file.isFile) {
                        files[filename] = file
                    } else {
                        System.err.println("warning: local file not exists: $file")
                    }
                }
            }
        }
        files.toImmutableMap()
    }

    /**
     * Check URL
     *
     * @param url URL
     * @return URL is accessible or not.
     */
    private fun checkURL(url: String): Boolean {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }

    companion object {
        const val DOCUMENT_FILENAME = "index.md"
        private const val HEADER_FILENAME = "head.%s.md"
        private const val FOOTER_FILENAME = "foot.%s.md"

        /**
         * Create document from directory.
         *
         * @param dir Document directory
         * @param name name for header/footer
         * @return Document
         */
        fun of(dir: File, name: String? = null): LocalDocument {
            val header = name?.let {
                val f = File(dir, HEADER_FILENAME.format(name))
                if (f.exists()) String(Files.readAllBytes(f.toPath())) + "\n" else ""
            } ?: ""
            val footer = name?.let {
                val f = File(dir, FOOTER_FILENAME.format(name))
                if (f.exists()) "\n" + String(Files.readAllBytes(f.toPath())) else ""
            } ?: ""

            // title と body に分離
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
            val body = bodyBuf.toString().replaceFirst("[\\r\\n]+".toRegex(), "")

            return LocalDocument(dir, title ?: "", header + body + footer)
        }
    }
}
