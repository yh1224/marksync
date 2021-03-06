package marksync.remote

import marksync.LocalDocument
import marksync.uploader.FileInfo
import marksync.uploader.Uploader
import java.io.File

/**
 * Remote service
 */
abstract class RemoteService(
    serviceName: String,
    val uploader: Uploader?
) {
    val metaFilename = "$METAFILE_PREFIX.$serviceName.yml"

    /**
     * Get all documents.
     *
     * @return Documents by keys
     */
    abstract fun getDocuments(): Map<String, RemoteDocument>

    /**
     * Get one document.
     *
     * @param id Document identifier
     * @return Document
     */
    abstract fun getDocument(id: String): RemoteDocument?

    /**
     * Get service document from Document.
     *
     * @param doc Document
     * @param dir Document directory
     * @return ServiceDocument object and expect digest
     */
    abstract fun toServiceDocument(doc: LocalDocument, dir: File): Pair<RemoteDocument, String?>?

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
    abstract fun saveMeta(doc: RemoteDocument, dir: File, files: ArrayList<FileInfo> = arrayListOf())

    /**
     * Update document to service.
     *
     * @param doc ServiceDocument object to update
     * @param message update message
     * @return Updated ServiceDocument object
     */
    abstract fun update(doc: RemoteDocument, message: String?): RemoteDocument?

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
        val (newDoc, expectDigest) = this.toServiceDocument(LocalDocument.of(dir), dir) ?: return

        // check
        var doSync = false
        val docId = newDoc.getDocumentId()
        if (docId != null) {
            val oldDoc = getDocument(docId)
            if (oldDoc == null) {
                println("? $target: ($docId) failed.")
            } else {
                val modifiedFiles = newDoc.files.filter { (filename, file) ->
                    !(newDoc.fileInfoList.find { it.filename == filename }?.isIdenticalTo(file) ?: false)
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
                newDoc.files.forEach { (filename, file) ->
                    if (newDoc.fileInfoList.find { it.filename == filename }?.isIdenticalTo(file) != true) {
                        val url = uploader.upload(file)
                        if (url == null) {
                            println("  ->upload failed. $filename")
                            return
                        }
                        newDoc.fileInfoList.find { it.filename == filename }?.let { newDoc.fileInfoList.remove(it) }
                        newDoc.fileInfoList.add(FileInfo(filename, file, url))
                        println("  ->uploaded. $filename to $url")
                    }
                }
            }

            // update document
            update(newDoc, message)?.also { updatedDoc ->
                println("  ->updated. ${updatedDoc.getDocumentUrl()}")
                saveMeta(updatedDoc, dir, newDoc.fileInfoList)
            } ?: println("  ->failed.")
        }
    }

    companion object {
        const val METAFILE_PREFIX = "marksync"
    }
}
