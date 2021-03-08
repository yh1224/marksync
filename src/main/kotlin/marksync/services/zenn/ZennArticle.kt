package marksync.services.zenn

import marksync.lib.UmlUtils
import marksync.services.ServiceDocument
import marksync.uploader.FileInfo
import org.apache.commons.codec.binary.Hex
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest

data class ZennArticle(
    val slug: String?,
    val url: String?,
    val type: String = "",
    val topics: List<String>,
    val published: Boolean,
    val title: String,
    val body: String,
    override val files: ArrayList<FileInfo> = arrayListOf()
) : ServiceDocument() {
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
        convertFiles(body).split("(?<=\n)".toRegex()).forEach { line ->
            // convert uml
            if (line.trim() == "```plantuml" || line.trim() == "```puml" || line.trim() == "```uml") {
                fUml = true
            } else if (fUml && line.trim() == "```") {
                val url = UmlUtils.convertToUrl(umlBody.toString())
                newBody.append("![]($url)")
                fUml = false
            } else if (fUml) {
                umlBody.append(line)
            } else {
                newBody.append(line)
            }
        }
        return newBody.toString()
    }

    override fun saveBody(file: File) {
        // write index.md
        val writer = FileWriter(file)
        writer.write("# ${this.title}\n\n${this.body}")
        writer.close()
    }

    override fun isModified(oldDoc: ServiceDocument, printDiff: Boolean): Boolean {
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
