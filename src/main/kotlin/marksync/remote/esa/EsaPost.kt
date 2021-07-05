package marksync.remote.esa

import marksync.remote.RemoteDocument
import marksync.uploader.FileInfo
import org.apache.commons.codec.binary.Hex
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest

data class EsaPost(
    val number: Int? = null,
    val url: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val category: String,
    val tags: List<String>,
    val wip: Boolean,
    val body_md: String,
    val name: String,
    override val files: MutableMap<String, File> = mutableMapOf(),
    override val fileInfoList: ArrayList<FileInfo> = arrayListOf()
) : RemoteDocument() {
    override fun getDocumentId(): String? = number?.toString()

    override fun getDocumentUrl(): String? = url

    override fun getDigest(): String =
        MessageDigest.getInstance("SHA-1").run {
            listOf(
                category,
                tags.joinToString(","),
                wip.toString(),
                getDocumentTitle(),
                getDocumentBody()
            ).forEach { update(it.toByteArray()) }
            Hex.encodeHexString(digest())
        }

    override fun getDocumentTitle(): String = name.replace('/', '∕')

    override fun getDocumentBody(): String {
        val newBody = StringBuilder()
        body_md.split("(?<=\n)".toRegex()).forEach { line ->
            when (line.trim()) {
                "```plantuml" ->
                    newBody.append(line.replace("^```plantuml".toRegex(), "```uml"))
                "```puml" ->
                    newBody.append(line.replace("^```puml".toRegex(), "```uml"))
                else ->
                    newBody.append(line)
            }
        }
        return convertFiles(newBody.toString())
    }

    override fun saveBody(file: File) {
        // write index.md
        val writer = FileWriter(file)
        writer.write("# ${this.name}\n\n${this.body_md}")
        writer.close()
    }

    override fun isModified(oldDoc: RemoteDocument, printDiff: Boolean): Boolean {
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
