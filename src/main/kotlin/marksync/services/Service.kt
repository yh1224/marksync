package marksync.services

import marksync.Document
import marksync.uploader.FileInfo
import marksync.uploader.Uploader
import java.io.File

/**
 * Service class.
 */
abstract class Service(
    serviceName: String,
    val uploader: Uploader?
) {
    val metaFilename = "$METAFILE_PREFIX.$serviceName.yml"

    /**
     * Get all documents.
     *
     * @return Documents by keys
     */
    abstract fun getDocuments(): Map<String, ServiceDocument>

    /**
     * Get one document.
     *
     * @param id Document identifier
     * @return Document
     */
    abstract fun getDocument(id: String): ServiceDocument?

    /**
     * Get service document from Document.
     *
     * @param doc Document
     * @param dir Document directory
     * @return ServiceDocument object and expect digest
     */
    abstract fun toServiceDocument(doc: Document, dir: File): Pair<ServiceDocument, String?>?

    /**
     * Create meta data for service.
     *
     * @param dir Document directory
     */
    abstract fun createMeta(dir: File)

    /**
     * Save meta data to file.
     *
     * @param doc Document
     * @param dir Document directory
     * @param files Files
     */
    abstract fun saveMeta(doc: ServiceDocument, dir: File, files: ArrayList<FileInfo> = arrayListOf())

    /**
     * Update document to service.
     *
     * @param doc ServiceDocument object to update
     * @param message update message
     * @return Updated ServiceDocument object
     */
    abstract fun update(doc: ServiceDocument, message: String?): ServiceDocument?

    /**
     * Sync documents to service.
     *
     * @param dir Target directory
     * @param force Force update
     * @param message update message
     * @param checkOnly Check only
     * @param showDiff Show diff
     */
    fun sync(dir: File, force: Boolean, message: String?, checkOnly: Boolean, showDiff: Boolean) {
        val target = dir.path
        val doc = Document.of(dir)
        val (newDoc, expectDigest) = this.toServiceDocument(doc, dir) ?: return

        // check
        var doSync = false
        val docId = newDoc.getDocumentId()
        if (docId != null) {
            val oldDoc = getDocument(docId)
            if (oldDoc == null) {
                println("? $target: ($docId) not exists.")
            } else {
                val modifiedFiles = doc.files.filter { (filename, file) ->
                    !(newDoc.files.find { it.filename == filename }?.isIdenticalTo(file) ?: false)
                }.map { it.key }
                val modified = newDoc.isModified(oldDoc) || modifiedFiles.isNotEmpty()
                val drifted = expectDigest != null && oldDoc.getDigest() != expectDigest
                if (!modified && !drifted) {
                    println("  $target: not modified.")
                } else {
                    if (drifted) {
                        println("x $target: modified externally.")
                        println("  $expectDigest:${oldDoc.getDigest()}")
                    } else {
                        println("! $target")
                    }
                    if (!drifted || force) {
                        doSync = true
                    }
                    if (showDiff) {
                        newDoc.isModified(oldDoc, printDiff = true)
                        if (modifiedFiles.isNotEmpty()) {
                            println("  [files]")
                            modifiedFiles.forEach { println("  !$it") }
                        }
                        println("  ----------------------------------------------------------------")
                    }
                }
            }
        } else {
            println("+ $target")
            doSync = true
        }

        // sync
        if (doSync && !checkOnly) {
            if (uploader != null) {
                // upload files
                doc.files.forEach { (filename, file) ->
                    if (newDoc.files.find { it.filename == filename }?.isIdenticalTo(file) != true) {
                        uploader.upload(file)?.also { url ->
                            newDoc.files.find { it.filename == filename }?.let { newDoc.files.remove(it) }
                            newDoc.files.add(FileInfo(filename, file, url))
                            println("  ->uploaded. $filename to $url")
                        } ?: println("  ->upload failed. $filename")
                    }
                }
            }

            // update document
            update(newDoc, message)?.also { updatedDoc ->
                println("  ->updated. ${updatedDoc.getDocumentUrl()}")
                saveMeta(updatedDoc, dir, newDoc.files)
            } ?: println("  ->failed.")
        }
    }

    companion object {
        const val METAFILE_PREFIX = "marksync"
    }
}
