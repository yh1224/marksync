package marksync.services.esa

import marksync.services.ServiceDocument
import marksync.uploader.FileInfo
import org.apache.commons.codec.binary.Hex
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest

data class EsaPost(
    val number: Int?,
    val url: String? = null,
    val created_at: String?,
    val updated_at: String?,
    val category: String? = "",
    val tags: List<String>,
    val wip: Boolean = true,
    val body_md: String,
    val name: String,
    val message: String? = null,
    override val files: ArrayList<FileInfo> = arrayListOf()
) : ServiceDocument() {
    override fun getDocumentId(): String? = number?.toString()

    override fun getDocumentUrl(): String? = url

    override fun getDigest(): String =
        MessageDigest.getInstance("SHA-1").run {
            listOf(
                category ?: "",
                tags.joinToString(","),
                wip.toString(),
                getDocumentTitle(),
                getDocumentBody()
            ).forEach { update(it.toByteArray()) }
            Hex.encodeHexString(digest())
        }

    override fun getDocumentTitle(): String = name.replace("/".toRegex(), "")

    override fun getDocumentBody(): String {
        val newBody = StringBuilder()
        convertFiles(body_md).split("(?<=\n)".toRegex()).forEach { line ->
            when (line.trim()) {
                "```plantuml" ->
                    newBody.append(line.replace("^```plantuml".toRegex(), "```uml"))
                "```puml" ->
                    newBody.append(line.replace("^```puml".toRegex(), "```uml"))
                else ->
                    newBody.append(line)
            }
        }
        return newBody.toString()
    }

    override fun saveBody(file: File) {
        // write index.md
        val writer = FileWriter(file)
        writer.write("# ${this.name}\n\n${this.body_md}")
        writer.close()
    }

    override fun isModified(oldDoc: ServiceDocument, printDiff: Boolean): Boolean {
        val oldPost = oldDoc as EsaPost
        return listOf(
            diff("category", oldPost.category, this.category, printDiff),
            diff("tags", oldPost.tags, this.tags, printDiff),
            diff("wip", oldPost.wip, this.wip, printDiff),
            diff("title", oldPost.name, this.getDocumentTitle(), printDiff),
            diff("body", oldPost.body_md, this.getDocumentBody(), printDiff)
        ).contains(true)
    }
}
