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

        /**
         * Create document from directory.
         *
         * @param dir Document directory
         * @return Document
         */
        fun of(dir: File): LocalDocument {
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
            return LocalDocument(
                dir,
                title ?: "",
                bodyBuf.toString().replaceFirst("[\\r\\n]+".toRegex(), "")
            )
        }
    }
}
