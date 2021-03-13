package marksync.remote.qiita

import marksync.LocalDocument
import marksync.lib.Mapper
import marksync.remote.RemoteDocument
import marksync.remote.RemoteService
import marksync.uploader.FileInfo
import marksync.uploader.Uploader
import java.io.File

/**
 * Qiita service class.
 *
 * @param serviceName Service name
 * @param username Username
 * @param accessToken Access Token
 * @param uploader Uploader
 */
class QiitaService(
    serviceName: String,
    private val username: String,
    accessToken: String,
    uploader: Uploader? = null
) : RemoteService(serviceName, uploader) {
    private val apiClient = QiitaApiClient(accessToken)

    private var items: List<QiitaItem>? = null

    override fun getDocuments(): Map<String, QiitaItem> {
        if (items == null) {
            items = apiClient.getItems(username)
        }
        return items!!.map { it.id!! to it }.toMap()
    }

    override fun getDocument(id: String): RemoteDocument? =
        items?.find { it.id == id } ?: apiClient.getItem(id)

    override fun toServiceDocument(doc: LocalDocument, dir: File): Pair<QiitaItem, String?>? {
        val metaFile = File(dir, metaFilename)

        return if (metaFile.exists()) {
            val itemMeta = Mapper.readYaml(metaFile, QiitaItemMeta::class.java)
            Pair(
                QiitaItem(
                    id = itemMeta.id,
                    url = itemMeta.url,
                    created_at = itemMeta.created_at,
                    updated_at = itemMeta.updated_at,
                    tags = itemMeta.tags,
                    `private` = itemMeta.`private`,
                    body = doc.body,
                    title = doc.title,
                    files = doc.files.toMutableMap(),
                    fileInfoList = itemMeta.files
                ),
                itemMeta.digest
            )
        } else null
    }

    override fun createMeta(dir: File) {
        val metaFile = File(dir, metaFilename)
        if (metaFile.exists()) {
            println("${metaFile.name} already exists.")
            return
        }

        // create new marksync.yml
        Mapper.writeYaml(metaFile, QiitaItemMeta())
        println("${metaFile.name} created.")
    }

    override fun saveMeta(doc: RemoteDocument, dir: File, files: ArrayList<FileInfo>) {
        val item = doc as QiitaItem
        val metaFile = File(dir, metaFilename)

        // update marksync.yml
        Mapper.writeYaml(
            metaFile, QiitaItemMeta(
                id = item.id,
                url = item.url,
                created_at = item.created_at,
                updated_at = item.updated_at,
                digest = item.getDigest(),
                tags = item.tags,
                `private` = item.`private`,
                files = files
            )
        )
    }

    override fun update(doc: RemoteDocument, message: String?): RemoteDocument? {
        val item = doc as QiitaItem
        val data = Mapper.getJson(
            mapOf(
                "body" to item.getDocumentBody(),
                "private" to item.`private`,
                "tags" to item.tags,
                "title" to item.getDocumentTitle()
            )
        )
        return item.id?.let { id ->
            apiClient.updateItem(id, data)
        } ?: apiClient.createItem(data)
    }
}
