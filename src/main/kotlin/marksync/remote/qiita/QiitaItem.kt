package marksync.remote.qiita

import marksync.lib.UmlUtils
import marksync.remote.RemoteDocument
import marksync.uploader.FileInfo
import org.apache.commons.codec.binary.Hex
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest

data class QiitaItem(
    val id: String? = null,
    val url: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val tags: List<QiitaItemTag>,
    val `private`: Boolean,
    val body: String,
    val title: String,
    override val files: MutableMap<String, File> = mutableMapOf(),
    override val fileInfoList: ArrayList<FileInfo> = arrayListOf()
) : RemoteDocument() {
    override fun getDocumentId() = id

    override fun getDocumentUrl() = url

    override fun getDigest(): String =
        MessageDigest.getInstance("SHA-1").run {
            listOf(
                tags.joinToString(","),
                `private`.toString(),
                getDocumentTitle(),
                getDocumentBody()
            ).forEach { update(it.toByteArray()) }
            return Hex.encodeHexString(digest())
        }

    override fun getDocumentTitle(): String = title

    /**
     * Convert markdown to Qiita.
     *
     * @return body
     */
    override fun getDocumentBody(): String {
        val newBody = StringBuilder()
        var fUml = false
        val umlBody = StringBuilder()
        body.split("(?<=\n)".toRegex()).forEach { line ->
            // convert uml
            if (line.trim() == "```plantuml" || line.trim() == "```puml" || line.trim() == "```uml") {
                fUml = true
            } else if (fUml && line.trim() == "```") {
                val pngFile = UmlUtils.convertToPng(umlBody.toString())
                if (!files.containsKey(pngFile.name)) {
                    files[pngFile.name] = pngFile
                }
                newBody.append("![](${pngFile.name})\n")
                fUml = false
            } else if (fUml) {
                umlBody.append(line)
            } else {
                newBody.append(line)
            }
        }
        return convertFiles(newBody.toString())
    }

    override fun saveBody(file: File) {
        // write index.md
        val writer = FileWriter(file)
        writer.write("# ${this.title}\n\n${this.body}")
        writer.close()
    }

    override fun isModified(oldDoc: RemoteDocument, printDiff: Boolean): Boolean {
        val oldItem = oldDoc as QiitaItem
        return listOf(
            diff("tags", oldItem.tags, this.tags, printDiff),
            diff("private", oldItem.`private`, this.`private`, printDiff),
            diff("title", oldItem.title, this.getDocumentTitle(), printDiff),
            diff("body", oldItem.body, this.getDocumentBody(), printDiff)
        ).contains(true)
    }
}
