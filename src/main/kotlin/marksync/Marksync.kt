package marksync

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.SystemExitException
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
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
    /**
     * Common arguments parser
     */
    open class CommonArgs(parser: ArgParser) {
        val command: String by parser
            .positional(
                "COMMAND",
                help = "Command (" + Command.values().filter { !it.deprecated }
                    .joinToString(", ") { it.name.toLowerCase() } + ")"
            )
        val env: String? by parser
            .storing("-e", "--env", help = "Environment")
            .default { null }
    }

    /**
     * arguments parser for import sub-command
     */
    class ImportArgs(parser: ArgParser) : CommonArgs(parser) {
        val output: File? by parser
            .storing("-o", "--output", help = "Output directory")
            { File(this) }
    }

    /**
     * arguments parser for status/diff/update sub-command
     */
    class UpdateArgs(parser: ArgParser) : CommonArgs(parser) {
        val recursive: Boolean by parser
            .flagging("-r", "--recursive", help = "Process recursively")
        val targets: List<File> by parser
            .positionalList("TARGETS", help = "Targets")
            { File(this) }
            .default { listOf(File(".")) }
    }

    /**
     * Command definition
     */
    enum class Command(
        val parser: (ArgParser) -> CommonArgs,
        val deprecated: Boolean = false
    ) {
        FETCH(::ImportArgs, true),
        IMPORT(::ImportArgs),
        NEW(::CommonArgs),
        CHECK(::UpdateArgs, true),
        STATUS(::UpdateArgs),
        DIFF(::UpdateArgs),
        UPDATE(::UpdateArgs)
    }

    fun run(args: Array<String>) = mainBody {
        val cmd = try {
            args.firstOrNull()?.let { Command.valueOf(it.toUpperCase()) }
        } catch (e: IllegalArgumentException) {
            throw SystemExitException("invalid command: ${args.first()}", -1)
        }
        ArgParser(args).parseInto(cmd?.parser ?: ::CommonArgs).let { cmdArgs ->
            val dotenv = getDotenv(cmdArgs.env) ?: throw SystemExitException("fatal: no marksync environment.", -1)
            when (cmd) {
                Command.IMPORT, Command.FETCH -> runImport(dotenv, cmdArgs as ImportArgs)
                Command.NEW -> runNew(dotenv)
                Command.CHECK, Command.STATUS -> runUpdate(dotenv, cmdArgs as UpdateArgs, checkOnly = true)
                Command.DIFF -> runUpdate(dotenv, cmdArgs as UpdateArgs, checkOnly = true, showDiff = true)
                Command.UPDATE -> runUpdate(dotenv, cmdArgs as UpdateArgs, checkOnly = false)
            }
        }
    }

    /**
     * run new sub-command
     */
    private fun runNew(dotenv: Dotenv) {
        createDocument(File("."), getService(dotenv)!!)
    }

    /**
     * run import sub-command
     */
    private fun runImport(dotenv: Dotenv, args: ImportArgs) {
        importAll(args.output!!, getService(dotenv)!!)
    }

    /**
     * run status/diff/update sub-command
     */
    private fun runUpdate(dotenv: Dotenv, args: UpdateArgs, checkOnly: Boolean, showDiff: Boolean = false) {
        // check targets
        args.targets.forEach { target ->
            if (!target.exists()) {
                throw SystemExitException("target not found: $target", -1)
            }
        }
        args.targets.forEach { target ->
            updateAll(
                target,
                getService(dotenv)!!,
                recursive = args.recursive,
                checkOnly = checkOnly,
                showDiff = showDiff
            )
        }
    }

    /**
     * Get Environment.
     *
     * @param envName Environment name
     * @return Dotenv object
     */
    private fun getDotenv(envName: String?): Dotenv? {
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
        val env = envCandidates.find { it.exists() && it.isFile } ?: return null
        return dotenv {
            filename = ENV_PREFIX
            directory = "/"
            filename = env.absolutePath
        }
    }

    /**
     * List all files recursively under the directory.
     *
     * @param dir Directory
     * @param recursive Scan files recursively
     * @return Files
     */
    private fun listFiles(dir: File, recursive: Boolean = true): List<File> {
        val these = dir.listFiles()!!.toList()
        return if (!recursive) these else
            these + these.filter { it.isDirectory }.flatMap { listFiles(it) }
    }

    /**
     * Import all documents from service.
     *
     * @param outDir Output directory
     * @param service Service object
     */
    private fun importAll(outDir: File, service: Service) {
        if (outDir.exists()) {
            println("$outDir already exists.")
            return
        }
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
     * @param recursive Process recursively
     * @param checkOnly Set true to check only
     * @param showDiff Show diff
     */
    private fun updateAll(
        fromDir: File,
        service: Service,
        recursive: Boolean,
        checkOnly: Boolean,
        showDiff: Boolean = false
    ) {
        if (!recursive) {
            println("warning: this process is no longer recursive by default. consider to use -r option.")
        }
        listFiles(fromDir, recursive)
            .filter { it.name == DOCUMENT_FILENAME }
            .forEach {
                service.sync(it.parentFile, checkOnly, showDiff)
            }
    }

    private fun createDocument(target: File, service: Service) {
        if (!File(target, DOCUMENT_FILENAME).exists()) {
            println("$DOCUMENT_FILENAME not found.")
            return
        }
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
