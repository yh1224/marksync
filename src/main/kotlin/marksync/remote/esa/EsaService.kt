package marksync.remote.esa

import marksync.LocalDocument
import marksync.lib.Mapper
import marksync.remote.RemoteDocument
import marksync.remote.RemoteService
import marksync.uploader.FileInfo
import marksync.uploader.Uploader
import java.io.File

/**
 * Esa service class.
 *
 * @param serviceName Service name
 * @param teamName Team name
 * @param username Username
 * @param accessToken Access Token
 * @param uploader Uploader
 */
class EsaService(
    serviceName: String = SERVICE_NAME,
    private val teamName: String,
    private val username: String,
    private val accessToken: String,
    uploader: Uploader? = null,
    private val apiClient: EsaApiClient = EsaApiClient(teamName, accessToken)
) : RemoteService(serviceName, uploader) {
    private var posts: List<EsaPost>? = null

    override fun getDocuments(): Map<String, EsaPost> {
        if (posts == null) {
            posts = apiClient.getPosts(username)
        }
        return posts!!.associateBy { post -> post.number!!.toString() }
    }

    override fun getDocument(id: String): RemoteDocument? =
        posts?.find { it.number == id.toInt() } ?: apiClient.getPost(username, id.toInt())

    override fun toServiceDocument(doc: LocalDocument, dir: File): Pair<EsaPost, String?>? {
        val metaFile = File(dir, metaFilename)
        return if (metaFile.exists()) {
            val postMeta = Mapper.readYaml(metaFile, EsaPostMeta::class.java)
            Pair(
                EsaPost(
                    number = postMeta.number,
                    url = postMeta.url,
                    created_at = postMeta.created_at,
                    updated_at = postMeta.updated_at,
                    category = postMeta.category,
                    tags = postMeta.tags,
                    wip = postMeta.wip,
                    name = doc.title,
                    body_md = doc.body,
                    files = doc.files.toMutableMap(),
                    fileInfoList = postMeta.files
                ),
                postMeta.digest
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
        Mapper.writeYaml(metaFile, EsaPostMeta())
        println("${metaFile.name} created.")
    }

    override fun saveMeta(doc: RemoteDocument, dir: File, files: ArrayList<FileInfo>) {
        val post = doc as EsaPost
        val metaFile = File(dir, metaFilename)

        // update marksync.yml
        Mapper.writeYaml(
            metaFile, EsaPostMeta(
                number = post.number,
                url = post.url,
                created_at = post.created_at,
                updated_at = post.updated_at,
                digest = post.getDigest(),
                category = post.category,
                tags = post.tags,
                wip = post.wip,
                files = files
            )
        )
    }

    override fun update(doc: RemoteDocument, message: String?): RemoteDocument? =
        apiClient.savePost(doc as EsaPost, message)

    companion object {
        const val SERVICE_NAME = "esa"
    }
}
