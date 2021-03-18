package marksync.remote.zenn

import marksync.lib.UmlUtils
import marksync.remote.RemoteDocument
import marksync.uploader.FileInfo
import org.apache.commons.codec.binary.Hex
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest

data class ZennArticle(
    val slug: String? = null,
    val url: String? = null,
    val type: String,
    val topics: List<String>,
    val published: Boolean,
    val title: String,
    val body: String,
    override val files: MutableMap<String, File> = mutableMapOf(),
    override val fileInfoList: ArrayList<FileInfo> = arrayListOf()
) : RemoteDocument() {
    override fun getDocumentId() = slug

    override fun getDocumentUrl() = url

    override fun getDigest(): String =
        MessageDigest.getInstance("SHA-1").run {
            listOf(
                type,
                topics.joinToString(","),
                published.toString(),
                getDocumentTitle(),
                getDocumentBody()
            ).forEach { update(it.toByteArray()) }
            return Hex.encodeHexString(digest())
        }

    override fun getDocumentTitle(): String = title

    /**
     * Convert markdown to Zenn.
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
        val oldItem = oldDoc as ZennArticle
        return listOf(
            diff("type", oldItem.type, this.type, printDiff),
            diff("topics", oldItem.topics, this.topics, printDiff),
            diff("published", oldItem.published, this.published, printDiff),
            diff("title", oldItem.title, this.getDocumentTitle(), printDiff),
            diff("body", oldItem.body, this.getDocumentBody(), printDiff)
        ).contains(true)
    }
}
