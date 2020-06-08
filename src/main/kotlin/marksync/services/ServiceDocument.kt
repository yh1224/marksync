package marksync.services

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import marksync.Mapper
import marksync.uploader.FileInfo
import java.io.File

/**
 * Service specific document class.
 */
abstract class ServiceDocument {
    abstract val files: ArrayList<FileInfo>

    /**
     * Get document identifier on this service.
     *
     * @return Document identifier
     */
    abstract fun getDocumentId(): String?

    /**
     * Get URL on this service.
     *
     * @return URL
     */
    abstract fun getDocumentUrl(): String?

    /**
     * Get digest.
     *
     * @return
     */
    abstract fun getDigest(): String

    /**
     * Get document title.
     *
     * @return document title
     */
    abstract fun getDocumentTitle(): String

    /**
     * Get body.
     *
     * @return body
     */
    abstract fun getDocumentBody(): String

    /**
     * Convert image tag to link primary site.
     *
     * @return Converted string
     */
    fun convertFiles(body: String): String {
        var result = body
        files.forEach { fileInfo ->
            result = result.replace(
                "\\[(.*)\\]\\(\\Q${fileInfo.filename}\\E\\)".toRegex(),
                "[$1](${fileInfo.url})"
            )
        }
        return result
    }

    /**
     * Save body data to file.
     *
     * @param file Target file
     */
    abstract fun saveBody(file: File)

    /**
     * Check modified.
     *
     * @param oldDoc Old document
     * @param printDiff true to print differ
     */
    abstract fun isModified(oldDoc: ServiceDocument, printDiff: Boolean = false): Boolean

    /**
     * Print diff.
     *
     * @param name Name
     * @param source Source string
     * @param target Target string
     * @param printDiff true to print differ
     * @return true:differ
     */
    fun <A> diff(name: String, source: A, target: A, printDiff: Boolean): Boolean {
        val valueOf: (A) -> List<String> = { obj ->
            if (obj is String) obj.trim().split("\r?\n".toRegex())
            else listOf(Mapper.getJson(obj))
        }
        val patch = DiffUtils.diff(valueOf(source), valueOf(target))
        if (printDiff && patch.deltas.isNotEmpty()) {
            println("  [$name]")
            UnifiedDiffUtils.generateUnifiedDiff(
                "original", "modified", valueOf(source), patch, 2
            ).drop(2).forEach { println("  $it") }
        }
        return patch.deltas.isNotEmpty()
    }
}
