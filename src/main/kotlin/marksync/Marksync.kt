package marksync

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import marksync.remote.RemoteDocument
import marksync.remote.RemoteService
import marksync.remote.esa.EsaService
import marksync.remote.esa.EsaUploader
import marksync.remote.qiita.QiitaService
import marksync.remote.zenn.ZennService
import marksync.uploader.S3Uploader
import marksync.uploader.Uploader
import picocli.CommandLine
import picocli.CommandLine.*
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

@Command(
    name = "marksync",
    subcommands = [HelpCommand::class],
    description = ["Marksync"]
)
class Marksync {
    @Option(names = ["-e", "--env"], paramLabel = "<env>", description = ["Environment name"])
    var envName: String? = null

    @Command(name = "import", description = ["Import documents"])
    @Suppress("unused")
    fun import(
        @Option(names = ["-o", "--output"], required = true, paramLabel = "<dir>", description = ["output directory"])
        output: File
    ): Int {
        runImport(envName, output)
        return 0
    }

    @Command(name = "new", description = ["Create new document metafile"])
    @Suppress("unused")
    fun new(): Int = try {
        runNew(envName)
        0
    } catch (e: MarksyncException) {
        System.err.println(e.message)
        e.code.num
    }

    @Command(name = "status", description = ["Check document update status"])
    @Suppress("unused")
    fun status(
        @Parameters(arity = "0..*", paramLabel = "<target>", description = ["Targets"])
        targets: List<File>?,
    ): Int = try {
        runUpdate(envName, targets ?: listOf(File(".")), checkOnly = true)
        0
    } catch (e: MarksyncException) {
        System.err.println(e.message)
        e.code.num
    }

    @Command(name = "diff", description = ["Differ document update"])
    @Suppress("unused")
    fun diff(
        @Parameters(arity = "0..*", paramLabel = "<target>", description = ["Targets"])
        targets: List<File>?,
    ): Int = try {
        runUpdate(envName, targets ?: listOf(File(".")), checkOnly = true, showDiff = true)
        0
    } catch (e: MarksyncException) {
        System.err.println(e.message)
        e.code.num
    }

    @Command(name = "update", description = ["Update document"])
    @Suppress("unused")
    fun update(
        @Option(names = ["-r", "--recursive"], description = ["Process recursively"])
        recursive: Boolean,

        @Option(names = ["-f", "--force"], description = ["Force update"])
        force: Boolean,

        @Option(names = ["-m", "--message"], paramLabel = "<message>", description = ["Update message"])
        message: String?,

        @Parameters(arity = "0..*", paramLabel = "<target>", description = ["Targets"])
        targets: List<File>?,
    ): Int = try {
        runUpdate(
            envName, targets ?: listOf(File(".")),
            recursive = recursive, force = force, message = message
        )
        0
    } catch (e: MarksyncException) {
        System.err.println(e.message)
        e.code.num
    }

    /**
     * run new sub-command
     */
    private fun runNew(envName: String?) {
        val dotenv = getDotenv(envName)
            ?: throw MarksyncException("fatal: no marksync environment.", MarksyncException.ErrorCode.ENV)
        val service = getService(dotenv)
            ?: throw MarksyncException("fatal: invalid environment.", MarksyncException.ErrorCode.ENV)
        createDocument(File("."), service)
    }

    /**
     * run import sub-command
     */
    private fun runImport(envName: String?, output: File) {
        val dotenv = getDotenv(envName)
            ?: throw MarksyncException("fatal: no marksync environment.", MarksyncException.ErrorCode.ENV)
        val service = getService(dotenv)
            ?: throw MarksyncException("fatal: invalid environment.", MarksyncException.ErrorCode.ENV)
        importAll(output, service)
    }

    /**
     * run status/diff/update sub-command
     */
    private fun runUpdate(
        envName: String?,
        targets: List<File>,
        recursive: Boolean = true,
        force: Boolean = false,
        message: String? = null,
        checkOnly: Boolean = false,
        showDiff: Boolean = false,
    ) {
        val dotenv = getDotenv(envName)
            ?: throw MarksyncException("fatal: no marksync environment.", MarksyncException.ErrorCode.ENV)
        val service = getService(dotenv)
            ?: throw MarksyncException("fatal: invalid environment.", MarksyncException.ErrorCode.ENV)

        // check targets
        targets.forEach { target ->
            if (!target.exists()) {
                throw MarksyncException("target not found: $target", MarksyncException.ErrorCode.TARGET)
            }
        }

        // proceed targets
        val files = targets.flatMap { target ->
            listFiles(target, recursive).filter { it.name == DOCUMENT_FILENAME }.map { it.parentFile }
        }
        if (files.count() >= PREFETCH_THRESHOLD) {
            service.prefetch()
        }
        files.forEach {
            service.sync(it, force, message, checkOnly, showDiff)
        }
    }

    /**
     * Find environment file.
     *
     * @param envName Environment name
     * @return Environment file
     */
    private fun findEnv(envName: String?): File? {
        val envCandidates = mutableListOf<File>()
        var path: Path? = Paths.get(".").toAbsolutePath()
        while (path != null) {
            if (envName != null) {
                envCandidates += path.resolve("$ENV_PREFIX/$envName").toFile()
            } else {
                envCandidates += path.resolve(ENV_PREFIX).toFile()
                envCandidates += path.resolve("$ENV_PREFIX/$ENV_DEFAULT").toFile()
            }
            path = path.parent
        }
        return envCandidates.find { it.exists() && it.isFile }
    }

    /**
     * Get Environment.
     *
     * @param envName Environment name
     * @return Dotenv object
     */
    private fun getDotenv(envName: String?): Dotenv? {
        val env = findEnv(envName) ?: return null
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
    private fun importAll(outDir: File, service: RemoteService) {
        if (outDir.exists()) {
            println("$outDir already exists.")
            return
        }
        service.getDocuments().forEach { (docId: String, doc: RemoteDocument) ->
            println("$docId ${doc.getDocumentUrl()}")
            val dir = File(outDir, docId)
            println("  -> ${dir.absolutePath}")
            dir.mkdirs()
            service.saveMeta(doc, dir)
            doc.saveBody(File(dir, LocalDocument.DOCUMENT_FILENAME))
        }
    }

    private fun createDocument(target: File, service: RemoteService) {
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
    private fun getService(dotenv: Dotenv): RemoteService? {
        val uploader = getUploader(dotenv)
        val service = dotenv["SERVICE"]
        val serviceName = dotenv["SERVICE_NAME"]
        return when (service) {
            QiitaService.SERVICE_NAME -> {
                val username = dotenv["QIITA_USERNAME"]
                val accessToken = dotenv["QIITA_ACCESS_TOKEN"]
                if (username != null && accessToken != null) {
                    QiitaService(serviceName ?: service, username, accessToken, uploader)
                } else null
            }
            EsaService.SERVICE_NAME -> {
                val team = dotenv["ESA_TEAM"]
                val username = dotenv["ESA_USERNAME"]
                val accessToken = dotenv["ESA_ACCESS_TOKEN"]
                if (team != null && username != null && accessToken != null) {
                    EsaService(serviceName ?: service, team, username, accessToken, uploader)
                } else null
            }
            ZennService.SERVICE_NAME -> {
                val username = dotenv["ZENN_USERNAME"]
                val gitDir = dotenv["ZENN_GIT_DIR"]
                val gitUrl = dotenv["ZENN_GIT_URL"]
                val gitBranch = dotenv["ZENN_GIT_BRANCH"]
                val gitUsername = dotenv["ZENN_GIT_USERNAME"]
                val gitPassword = dotenv["ZENN_GIT_PASSWORD"]
                if (username != null && gitDir != null
                    && gitUrl != null && gitBranch != null && gitUsername != null && gitPassword != null
                ) {
                    ZennService(
                        serviceName ?: service, username,
                        gitDir, gitUrl, gitBranch, gitUsername, gitPassword,
                        uploader
                    )
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
        const val PREFETCH_THRESHOLD = 25

        @JvmStatic
        fun run(args: Array<String>) {
            val exitCode = CommandLine(Marksync()).execute(*args)
            exitProcess(exitCode)
        }
    }
}
