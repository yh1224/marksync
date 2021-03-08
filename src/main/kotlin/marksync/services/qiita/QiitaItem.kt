package marksync.services.qiita

import marksync.services.ServiceDocument
import marksync.uploader.FileInfo
import net.sourceforge.plantuml.code.TranscoderUtil
import org.apache.commons.codec.binary.Hex
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest
import net.sourceforge.plantuml.SourceStringReader




data class QiitaItem(
    val id: String?,
    val url: String?,
    val created_at: String?,
    val updated_at: String?,
    val tags: List<QiitaItemTag>,
    val `private`: Boolean,
    val body: String,
    val title: String,
    override val files: ArrayList<FileInfo> = arrayListOf()
) : ServiceDocument() {
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
     * Convert UML tag to use PlantUML server.
     *
     * @param umlBody UML string
     * @return Converted string
     */
    fun convertUml(umlBody: String): String {
        val reader = SourceStringReader(umlBody)
        val uml = "@startuml\n$umlBody\n@enduml\n"
        val encodedUml = TranscoderUtil.getDefaultTranscoder().encode(uml)
        return "![](http://www.plantuml.com/plantuml/svg/$encodedUml)\n"
    }

    /**
     * Convert markdown to Qiita.
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
                newBody.append(convertUml(umlBody.toString()))
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
        val oldItem = oldDoc as QiitaItem
        return listOf(
            diff("tags", oldItem.tags, this.tags, printDiff),
            diff("private", oldItem.`private`, this.`private`, printDiff),
            diff("title", oldItem.title, this.getDocumentTitle(), printDiff),
            diff("body", oldItem.body, this.getDocumentBody(), printDiff)
        ).contains(true)
    }
}
