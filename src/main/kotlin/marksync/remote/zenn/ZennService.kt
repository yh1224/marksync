package marksync.remote.zenn

import marksync.LocalDocument
import marksync.lib.Mapper
import marksync.remote.RemoteDocument
import marksync.remote.RemoteService
import marksync.uploader.FileInfo
import marksync.uploader.Uploader
import java.io.File

/**
 * Zenn service class.
 *
 * @param serviceName Service name
 * @param username Zenn Username
 * @param gitDir GitHub Directory
 * @param gitUrl GitHub Repository URL
 * @param gitBranch GitHub Repository Branch
 * @param gitUsername GitHub Username
 * @param gitPassword GitHub Password
 * @param uploader Uploader
 */
class ZennService(
    serviceName: String,
    username: String,
    gitDir: String,
    gitUrl: String,
    gitBranch: String,
    gitUsername: String,
    gitPassword: String,
    uploader: Uploader? = null
) : RemoteService(serviceName, uploader) {
    private val zennRepository =
        ZennRepository(username, gitDir, gitUrl, gitBranch, gitUsername, gitPassword)

    private var articles: List<ZennArticle>? = null

    override fun getDocuments(): Map<String, ZennArticle> {
        if (articles == null) {
            articles = zennRepository.getArticles()
        }
        return articles!!.map { it.slug!! to it }.toMap()
    }

    override fun getDocument(id: String): RemoteDocument? =
        articles?.find { it.slug == id } ?: zennRepository.getArticle(id)

    override fun toServiceDocument(doc: LocalDocument, dir: File): Pair<ZennArticle, String?>? {
        val metaFile = File(dir, metaFilename)
        return if (metaFile.exists()) {
            val articleMeta = Mapper.readYaml(metaFile, ZennDocMeta::class.java)
            Pair(
                ZennArticle(
                    slug = articleMeta.slug,
                    url = articleMeta.url,
                    type = articleMeta.type,
                    topics = articleMeta.topics,
                    published = articleMeta.published,
                    title = doc.title,
                    body = doc.body,
                    fileInfoList = articleMeta.files
                ),
                articleMeta.digest
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
        Mapper.writeYaml(metaFile, ZennDocMeta())
        println("${metaFile.name} created.")
    }

    override fun saveMeta(doc: RemoteDocument, dir: File, files: ArrayList<FileInfo>) {
        val article = doc as ZennArticle
        val metaFile = File(dir, metaFilename)

        // update marksync.yml
        Mapper.writeYaml(
            metaFile, ZennDocMeta(
                slug = article.slug,
                url = article.url,
                digest = article.getDigest(),
                type = article.type,
                topics = article.topics,
                published = article.published,
                files = files
            )
        )
    }

    override fun update(doc: RemoteDocument, message: String?): RemoteDocument? {
        return zennRepository.saveArticle(doc as ZennArticle, message)
    }
}
