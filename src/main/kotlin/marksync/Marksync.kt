package marksync

import com.xenomachina.argparser.*
import io.github.cdimascio.dotenv.DotEnvException
import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import marksync.services.Service
import marksync.services.ServiceDocument
import marksync.services.esa.EsaService
import marksync.services.esa.EsaUploader
import marksync.services.qiita.QiitaService
import marksync.uploader.S3Uploader
import marksync.uploader.Uploader
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class Marksync {
    class MarksyncArgs(parser: ArgParser) {
        val subCommand: String by parser
            .positional("SUBCOMMAND", help = "Subcommand")
        val env: String? by parser
            .storing("-e", "--env", help = "Environment")
            .default { null }
        val output: File? by parser
            .storing("-o", "--output", help = "Output directory")
            { File(this) }
            .default { null }
            .addValidator {
                if (subCommand == "fetch" && value == null) {
                    throw InvalidArgumentException("missing output directory option.")
                }
            }
        val targets: List<File> by parser
            .positionalList("TARGETS", help = "Targets")
            { File(this) }
            .default { listOf(File(".")) }
    }

    fun run(args: Array<String>) = mainBody {
        ArgParser(args).parseInto(::MarksyncArgs).run {
            // check targets
            targets.forEach { target ->
                if (!target.exists()) {
                    throw SystemExitException("target not found: $target", -1)
                }
            }
            try {
                val dotenv = getDotenv(env)
                when (subCommand) {
                    "fetch" -> {
                        fetchAll(output!!, getService(dotenv)!!)
                    }
                    "new" -> targets.forEach { target ->
                        createDocument(target, getService(dotenv)!!)
                    }
                    "check", "status" -> targets.forEach { target ->
                        updateAll(target, getService(dotenv)!!, checkOnly = true)
                    }
                    "diff" -> targets.forEach { target ->
                        updateAll(target, getService(dotenv)!!, checkOnly = true, showDiff = true)
                    }
                    "update" -> targets.forEach { target ->
                        updateAll(target, getService(dotenv)!!, checkOnly = false)
                    }
                    else -> throw SystemExitException("invalid SUBCOMMAND: $subCommand", -1)
                }
            } catch (e: DotEnvException) {
                throw SystemExitException("Could not find environment: $env.", -1)
            }
        }
    }

    /**
     * Get Environment.
     *
     * @param envName Environment name
     * @return Dotenv object
     */
    private fun getDotenv(envName: String?): Dotenv {
        val envCandidates = mutableListOf<File>()
        var path: Path? = Paths.get(".").toAbsolutePath()
        while (path != null) {
            if (envName != null) {
                envCandidates += path.resolve("$ENV_PREFIX.$envName").toFile()
                envCandidates += path.resolve("$ENV_PREFIX/$envName").toFile()
            } else {
                envCandidates += path.resolve(ENV_PREFIX).toFile()
                envCandidates += path.resolve("$ENV_PREFIX/$ENV_DEFAULT").toFile()
            }
            path = path.parent
        }
        return dotenv {
            filename = ENV_PREFIX
            envCandidates.find { it.exists() && it.isFile }?.let { file ->
                directory = "/"
                filename = file.absolutePath
            }
            if (envName == null) {
                ignoreIfMissing = true
            }
        }
    }

    /**
     * List all files recursively under the directory.
     *
     * @param dir Directory
     * @return Files
     */
    private fun listFiles(dir: File): List<File> {
        val these = dir.listFiles()!!.toList()
        return these + these.filter { it.isDirectory }.flatMap { listFiles(it) }
    }

    /**
     * Fetch all documents from service.
     *
     * @param outDir Output directory
     * @param service Service object
     */
    private fun fetchAll(outDir: File, service: Service) {
        service.getDocuments().forEach { (docId: String, doc: ServiceDocument) ->
            println("$docId ${doc.getDocumentUrl()}")
            val dir = File(outDir, docId)
            println("  -> ${dir.absolutePath}")
            dir.mkdirs()
            service.saveMeta(doc, dir)
            doc.saveBody(File(dir, Document.DOCUMENT_FILENAME))
        }
    }

    /**
     * Check/Update all documents under the directory.
     *
     * @param fromDir Input directory path
     * @param service Service object
     * @param checkOnly Set true to check only
     * @param showDiff Show diff
     */
    private fun updateAll(fromDir: File, service: Service, checkOnly: Boolean, showDiff: Boolean = false) {
        listFiles(fromDir)
            .filter { it.name == DOCUMENT_FILENAME }
            .forEach {
                service.sync(it.parentFile, checkOnly, showDiff)
            }
    }

    private fun createDocument(target: File, service: Service) {
        service.createMeta(target)
    }

    /**
     * Get service from env.
     *
     * @param dotenv Environment
     * @return Service
     */
    private fun getService(dotenv: Dotenv): Service? {
        val uploader = getUploader(dotenv)
        return when (dotenv["SERVICE"]) {
            "qiita" -> {
                val username = dotenv["QIITA_USERNAME"]
                val accessToken = dotenv["QIITA_ACCESS_TOKEN"]
                if (username != null && accessToken != null) {
                    QiitaService(username, accessToken, uploader)
                } else null
            }
            "esa" -> {
                val team = dotenv["ESA_TEAM"]
                val username = dotenv["ESA_USERNAME"]
                val accessToken = dotenv["ESA_ACCESS_TOKEN"]
                if (team != null && username != null && accessToken != null) {
                    EsaService(team, username, accessToken, uploader)
                } else null
            }
            else -> null
        }
    }

    /**
     * Get uploader from env.
     *
     * @param dotenv Environment
     * @return Uploader
     */
    private fun getUploader(dotenv: Dotenv): Uploader? {
        return if (dotenv["UPLOADER"] == "s3") {
            val bucketName = dotenv["S3_BUCKET_NAME"]
            val prefix = dotenv["S3_PREFIX"]
            val baseUrl = dotenv["S3_BASE_URL"]
            if (bucketName != null && baseUrl != null) {
                S3Uploader(bucketName, prefix, baseUrl, dotenv)
            } else null
        } else if (dotenv["SERVICE"] == "esa") {
            val team = dotenv["ESA_TEAM"]
            val accessToken = dotenv["ESA_ACCESS_TOKEN"]
            if (team != null && accessToken != null) {
                EsaUploader(team, accessToken)
            } else null
        } else {
            null
        }
    }

    companion object {
        const val ENV_PREFIX = ".marksync"
        const val ENV_DEFAULT = "default"
        const val DOCUMENT_FILENAME = "index.md"
    }
}
