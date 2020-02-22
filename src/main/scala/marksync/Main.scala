package marksync

import java.io.File
import java.nio.file.Paths

import io.github.cdimascio.dotenv.Dotenv
import marksync.esa.{EsaService, EsaUploader}
import marksync.qiita.{QiitaService, S3Uploader}

import scala.collection.mutable.ListBuffer

class Main extends xsbti.AppMain {
  val CONFIG_DIR = ".marksync"
  val ENV_PREFIX = ".env"

  class Exit(val code: Int) extends xsbti.Exit

  case class Config
  (
    subCommand: String = "",
    env: String = ".env",
    verbose: Boolean = false,
    output: Option[File] = None,
    targets: Seq[File] = List(),
  )

  def run(configuration: xsbti.AppConfiguration): xsbti.MainResult = {
    val parser = new scopt.OptionParser[Config]("marksync") {
      head("scopt", "3.7.1")
      opt[String]('e', "env")
        .action { (x, c) =>
          c.copy(env = x)
        } text ("environment")
      opt[Unit]('v', "verbose")
        .action { (_, c) =>
          c.copy(verbose = true)
        } text ("verbose")
      help("help") text ("marksync")
      cmd("fetch")
        .action((_, c) => c.copy(subCommand = "fetch"))
        .children(
          opt[File]('o', "output")
            .required()
            .action { (x, c) =>
              c.copy(output = Some(x))
            } text ("output"),
        )
      cmd("check")
        .action((_, c) => c.copy(subCommand = "check"))
        .children(
          arg[File]("<target>...")
            .unbounded()
            .action((x, c) => c.copy(targets = c.targets :+ x))
        )
      cmd("update")
        .action((_, c) => c.copy(subCommand = "update"))
        .children(
          arg[File]("<target>...")
            .unbounded()
            .action((x, c) => c.copy(targets = c.targets :+ x))
        )
      checkConfig { c =>
        if (c.subCommand.isEmpty) failure("subcommand required.")
        else success
      }
    }
    parser.parse(configuration.arguments(), Config()).foreach { config =>
      config.targets.foreach { target =>
        if (!target.exists()) {
          println("not found: " + target)
          sys.exit(-1)
        }
      }
      val dotEnv = getDotEnv(config.env)
      if (dotEnv.isEmpty) {
        println("file not found: " + config.env)
      } else {
        dotEnv.foreach { dotEnv =>
          config.subCommand match {
            case "fetch" =>
              fetchAll(config.output.get, getService(dotEnv))
            case "check" =>
              config.targets.foreach { target =>
                updateAll(target, getService(dotEnv), getUploader(dotEnv), check = true, verbose = config.verbose)
              }
            case "update" =>
              config.targets.foreach { target =>
                updateAll(target, getService(dotEnv), getUploader(dotEnv), check = false, verbose = config.verbose)
              }
          }
        }
      }
    }
    new Exit(0)
  }

  /**
   * Get dotenv.
   *
   * @param env Environment name
   * @return Dotenv object
   */
  def getDotEnv(env: String): Option[Dotenv] = {
    val envCandidates = ListBuffer[String]()
    var path = Paths.get(".").toAbsolutePath
    while (path != null) {
      envCandidates += path.resolve(env).toString
      envCandidates += path.resolve(ENV_PREFIX + "." + env).toString
      path = path.getParent
    }
    envCandidates
      .find(f => new File(f).exists)
      .map(envFile => Dotenv.configure().directory("/").filename(envFile).load())
  }

  /**
   * List all files recursively under the directory.
   *
   * @param dir Directory
   * @return Files
   */
  def listFiles(dir: File): Array[File] = {
    val these = dir.listFiles
    these ++ these.filter(_.isDirectory).flatMap(listFiles)
  }

  /**
   * Fetch all documents from service.
   *
   * @param outDir  Output directory
   * @param service Service object
   */
  def fetchAll(outDir: File, service: Service): Unit = {
    service.getDocuments.foreach { case (docId: String, doc: ServiceDocument) =>
      println(s"$docId ${doc.getUrl.get}")
      val dir = new File(outDir, docId)
      println(s"  -> ${dir.getAbsolutePath}")
      dir.mkdirs()
      service.saveMeta(new File(dir, service.META_FILENAME), doc)
      doc.saveBody(new File(dir, Document.DOCUMENT_FILENAME))
    }
  }

  /**
   * Sync all documents under the directory.
   *
   * @param fromDir  Input directory path
   * @param service  Service object
   * @param uploader Uploader
   * @param check    Set true to check only
   * @param verbose  Output verbose message
   */
  def updateAll(fromDir: File, service: Service, uploader: Option[Uploader], check: Boolean, verbose: Boolean): Unit = {
    listFiles(fromDir)
      .filter(_.getName == "index.md")
      .map(_.getParentFile)
      .foreach { dir =>
        val doc = Document(dir)
        service.sync(doc, uploader, check, verbose)
      }
  }

  /**
   * Get service from env.
   *
   * @param dotEnv Environment
   * @return Service
   */
  def getService(dotEnv: Dotenv): Service = {
    dotEnv.get("SERVICE") match {
      case "qiita" =>
        new QiitaService(dotEnv.get("QIITA_USERNAME"), dotEnv.get("QIITA_ACCESS_TOKEN"))
      case "esa" =>
        new EsaService(dotEnv.get("ESA_TEAM"), dotEnv.get("ESA_USERNAME"), dotEnv.get("ESA_ACCESS_TOKEN"))
    }
  }

  /**
   * Get uploader from env.
   *
   * @param dotEnv Environment
   * @return Uploader
   */
  def getUploader(dotEnv: Dotenv): Option[Uploader] = {
    Option(dotEnv.get("UPLOADER")) match {
      case Some("s3") =>
        Some(new S3Uploader(
          dotEnv.get("S3_BUCKET_NAME"),
          dotEnv.get("S3_PREFIX"),
          dotEnv.get("S3_BASE_URL"),
          dotEnv.get("AWS_PROFILE")))
      case None =>
        dotEnv.get("SERVICE") match {
          case "esa" => Some(new EsaUploader(
            dotEnv.get("ESA_TEAM"),
            dotEnv.get("ESA_USERNAME"),
            dotEnv.get("ESA_ACCESS_TOKEN")))
          case _ => None
        }
    }
  }
}
