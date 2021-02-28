package marksync.services.zenn

import marksync.Mapper
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.File
import java.nio.file.Files


class ZennRepository(
    private val username: String,
    gitDir: String,
    gitUrl: String,
    private val gitBranch: String,
    gitUsername: String,
    gitPassword: String
) {
    private val credentialsProvider = UsernamePasswordCredentialsProvider(gitUsername, gitPassword)
    private val localGitDir = File(gitDir)
    private val workDir: File by lazy {
        File.createTempFile("marksync-zenn", "").also {
            Files.delete(it.toPath())
            it.mkdirs()
        }
    }

    private val git: Git by lazy {
        if (localGitDir.isDirectory) {
            val repository = RepositoryBuilder()
                .setGitDir(localGitDir)
                .setWorkTree(workDir)
                .build()
            Git(repository).apply {
                fetch()
                    .setCredentialsProvider(credentialsProvider)
                    .call()
            }
        } else {
            localGitDir.mkdirs()
            Git.cloneRepository()
                .setURI(gitUrl)
                .setCredentialsProvider(credentialsProvider)
                .setGitDir(localGitDir)
                .setDirectory(workDir)
                .call()
        }
    }

    data class ZennArticleMeta(
        val type: String = "",
        val topics: List<String> = listOf(),
        val published: Boolean = false,
        val title: String = ""
    )

    /**
     * List article slugs.
     */
    private fun listArticleSlugs(): List<String> {
        val rw = RevWalk(git.repository)
        val tw = TreeWalk(git.repository).apply {
            addTree(rw.parseCommit(git.repository.resolve(Constants.HEAD)).tree)
            filter = PathFilter.create("articles")
            isRecursive = true
        }
        val files = mutableListOf<String>()
        while (tw.next()) {
            val pattern = "^articles/([0-9a-z]+)\\.md$".toRegex()
            if (pattern.matches(tw.pathString)) {
                val slug = pattern.replace(tw.pathString) { it.groupValues[1] }
                files.add(slug)
            }
        }
        return files
    }

    /**
     * Get articles.
     */
    fun getArticles(): List<ZennArticle> = listArticleSlugs().map { getArticle(it)!! }

    /**
     * Get article.
     */
    fun getArticle(slug: String): ZennArticle? {
        val filePath = "${ARTICLES_PATH}/${slug}.md"
        val file = File(workDir, filePath).also { it.deleteOnExit() }
        git.checkout().setName(gitBranch).addPath(filePath).call()
        if (!file.exists()) {
            return null
        }

        val contents = file.absoluteFile.inputStream().readBytes().toString(Charsets.UTF_8).split("---\n")
        return if (contents.count() == 3) {
            val meta = Mapper.readYaml(contents[1], ZennArticleMeta::class.java)
            ZennArticle(
                slug = slug,
                url = zennUrl(slug),
                type = meta.type,
                topics = meta.topics,
                published = meta.published,
                title = meta.title,
                body = contents[2]
            )
        } else null
    }

    /**
     * Save article.
     */
    fun saveArticle(article: ZennArticle, message: String?): ZennArticle {
        val slug = article.slug ?: newSlug()

        val filePath = "${ARTICLES_PATH}/${slug}.md"
        val file = File(workDir, filePath).also { it.deleteOnExit() }
        git.checkout().setName(gitBranch).addPath(filePath).call()
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        val content = """
            ---
            title: "${article.title}"
            type: "${article.type}"
            topics: [${article.topics.joinToString(", ") { "\"${it}\"" }}]
            published: ${article.published}
            ---
            """.trimIndent() + "\n" + article.getDocumentBody()
        file.absoluteFile.outputStream().write(content.toByteArray(Charsets.UTF_8))

        git.add().addFilepattern(filePath).call()
        git.commit().setMessage(message ?: "update $slug").call()
        git.push().setCredentialsProvider(credentialsProvider).call()

        return article.copy(slug = slug, url = zennUrl(slug))
    }

    /**
     * Get zenn URL.
     */
    private fun zennUrl(slug: String): String = "https://zenn.dev/${username}/articles/${slug}"

    /**
     * Generate new slug.
     */
    private fun newSlug(): String {
        val allowedChars = ('0'..'9') + ('a'..'z')
        return (1..17).map { allowedChars.random() }.joinToString("")
    }

    companion object {
        const val ARTICLES_PATH = "articles"
    }
}
