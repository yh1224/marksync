package marksync.services.zenn

import marksync.Document
import marksync.Mapper
import marksync.services.Service
import marksync.services.ServiceDocument
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
) : Service(serviceName, uploader) {
    private val zennRepository =
        ZennRepository(username, gitDir, gitUrl, gitBranch, gitUsername, gitPassword)

    private var articles: List<ZennArticle>? = null

    override fun getDocuments(): Map<String, ZennArticle> {
        if (articles == null) {
            articles = zennRepository.getArticles()
        }
        return articles!!.map { it.slug!! to it }.toMap()
    }

    override fun getDocument(id: String): ServiceDocument? =
        articles?.find { it.slug == id } ?: zennRepository.getArticle(id)

    override fun toServiceDocument(doc: Document, dir: File): Pair<ZennArticle, String?>? {
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
                    files = articleMeta.files
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

    override fun saveMeta(doc: ServiceDocument, dir: File, files: ArrayList<FileInfo>) {
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

    override fun update(doc: ServiceDocument, message: String?): ServiceDocument? {
        return zennRepository.saveArticle(doc as ZennArticle, message)
    }
}
