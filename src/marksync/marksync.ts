import {ArgumentParser} from "argparse";
import * as dotenv from "dotenv";
import * as fs from "fs";
import * as path from "path";
import {EsaApiClient} from "./remote/esa/api";
import {EsaUploader} from "./remote/esa/uploader";
import {EsaService} from "./remote/esa/service";
import {QiitaApiClient} from "./remote/qiita/api";
import {QiitaService} from "./remote/qiita/service";
import {ZennRepository} from "./remote/zenn/repository";
import {ZennService} from "./remote/zenn/service";
import {RemoteService} from "./remote/service";
import {S3Uploader} from "./uploader/s3";
import {Uploader} from "./uploader/uploader";
import {MarksyncEnv} from "./env";
import {LocalDocument} from "./document";

const {version} = require("../../package.json");

export class Marksync {
    static readonly ENV_PREFIX = ".marksync";
    static readonly ENV_DEFAULT = "default";
    static readonly PREFETCH_THRESHOLD = 25;

    async run(): Promise<void> {
        const parser = new ArgumentParser({"prog": "marksync"});
        parser.add_argument("-v", "--version", {action: "version", version});
        parser.add_argument("-e", "--env", {help: "environment name"});
        const subparsers = parser.add_subparsers();

        // sub-command: import
        const parser_import = subparsers.add_parser("import", {description: "Import documents"});
        parser_import.add_argument("-o", "--output", {required: true, metavar: "<dir>", help: "output directory"});
        parser_import.set_defaults({
            "func": async () => {
                await this.runImport(args.env, args.output);
            }
        });

        // sub-command: new
        const parser_new = subparsers.add_parser("new", {description: "Create new document metafile"});
        parser_new.set_defaults({
            "func": async () => {
                this.runNew(args.env);
            }
        });

        // sub-command: status
        const parser_status = subparsers.add_parser("status", {description: "Check document update status"});
        parser_status.add_argument("targets", {metavar: "<target>", nargs: "*", default: ["."], help: "targets"});
        parser_status.set_defaults({
            "func": async () => {
                await this.runUpdate(args.env, args.targets, true, false, null, true);
            }
        });

        // sub-command: diff
        const parser_diff = subparsers.add_parser("diff", {description: "Differ document update"});
        parser_diff.add_argument("targets", {metavar: "<target>", nargs: "*", default: ["."], help: "targets"});
        parser_diff.set_defaults({
            "func": async () => {
                await this.runUpdate(args.env, args.targets, true, false, null, true, true);
            }
        });

        // sub-command: update
        const parser_update = subparsers.add_parser("update", {description: "Update document"});
        parser_update.add_argument("-r", "--recursive", {action: "store_true", help: "process recursively"});
        parser_update.add_argument("-f", "--force", {action: "store_true", help: "force update"});
        parser_update.add_argument("-m", "--message", {metavar: "<message>", help: "update message"});
        parser_update.add_argument("targets", {metavar: "<target>", nargs: "*", default: ["."], help: "targets"});
        parser_update.set_defaults({
            "func": async () => {
                await this.runUpdate(args.env, args.targets, args.recursive, args.force, args.message);
            }
        });

        const args = parser.parse_args();
        if (args.func) {
            await args.func(args);
        } else {
            parser.print_usage();
        }
    }

    /**
     * run new sub-command
     */
    runNew(envName: string): void {
        const service = this.getService(this.getDotenv(envName));
        if (!service) return;

        this.createDocument(".", service);
    }

    /**
     * run import sub-command
     */
    async runImport(envName: string, output: string): Promise<void> {
        const service = this.getService(this.getDotenv(envName));
        if (!service) return;

        await this.importAll(output, service);
    }

    /**
     * run status/diff/update sub-command
     */
    async runUpdate(envName: string, targets: string[], recursive: boolean = true, force: boolean = false, message: string | null, checkOnly: boolean = false, showDiff: boolean = false): Promise<void> {
        const service = this.getService(this.getDotenv(envName));
        if (!service) return;

        // check targets
        for (const target of targets) {
            if (!fs.existsSync(target)) {
                process.stderr.write(`target not found: ${target}\n`);
                return;
            }
        }

        // proceed targets
        const files = targets.flatMap(target =>
            this.listFiles(target, recursive)
                .filter(it => path.basename(it) == LocalDocument.DOCUMENT_FILENAME)
                .map(it => path.dirname(it))
                .sort()
        );
        if (files.length >= Marksync.PREFETCH_THRESHOLD) {
            await service.prefetch();
        }
        for (const file of files) {
            await service.sync(file, force, message, checkOnly, showDiff);
        }
    }

    /**
     * Find environment file.
     *
     * @param envName Environment name
     * @return Environment file path
     */
    private findEnv(envName?: string): string | null {
        const envCandidates = [];
        let p = process.cwd();
        while (p.length > 1) {
            if (envName) {
                envCandidates.push(`${p}/${Marksync.ENV_PREFIX}/${envName}`);
            } else {
                envCandidates.push(`${p}/${Marksync.ENV_PREFIX}`);
                envCandidates.push(`${p}/${Marksync.ENV_PREFIX}/${Marksync.ENV_DEFAULT}`);
            }
            p = path.dirname(p);
        }
        return envCandidates.find(it => fs.existsSync(it) && fs.lstatSync(it).isFile()) || null;
    }

    /**
     * Get Environment.
     *
     * @param envName Environment name
     * @return Dotenv object
     */
    private getDotenv(envName?: string): MarksyncEnv {
        const envFilePath = this.findEnv(envName);
        if (!envFilePath) {
            return {} as MarksyncEnv;
        }
        dotenv.config({path: envFilePath});
        return (process.env as unknown) as MarksyncEnv;
    }

    /**
     * List all files recursively under the directory.
     *
     * @param dirPath Directory path
     * @param recursive Scan files recursively
     * @return Files
     */
    private listFiles(dirPath: string, recursive: boolean = true): string[] {
        let result: string[] = [];
        for (const f of fs.readdirSync(dirPath)) {
            const filePath = path.join(dirPath, f);
            if (recursive && fs.statSync(filePath).isDirectory()) {
                result = result.concat(this.listFiles(filePath));
            } else {
                result.push(filePath);
            }
        }
        return result;
    }

    // noinspection JSMethodCanBeStatic
    /**
     * Import all documents from service.
     *
     * @param outDir Output directory
     * @param service Service object
     */
    private async importAll(outDir: string, service: RemoteService): Promise<void> {
        if (fs.existsSync(outDir)) {
            process.stderr.write(`ERROR: ${outDir} already exists.\n`);
            return;
        }

        const documents = await service.getDocuments();
        if (documents === null) {
            return;
        }
        for (const [docId, doc] of Object.entries(documents)) {
            process.stdout.write(`${docId} ${doc.getDocumentUrl()}\n`);
            const dir = path.join(outDir, docId);
            process.stdout.write(`  -> ${dir}\n`);
            fs.mkdirSync(dir, {recursive: true});
            await service.saveMeta(doc, dir);
            doc.saveBody(path.join(dir, LocalDocument.DOCUMENT_FILENAME));
        }
    }

    // noinspection JSMethodCanBeStatic
    private createDocument(target: string, service: RemoteService): void {
        if (!fs.existsSync(`${target}/${LocalDocument.DOCUMENT_FILENAME}`)) {
            process.stderr.write(`ERROR: ${LocalDocument.DOCUMENT_FILENAME} not found.\n`);
            return;
        }
        service.createMeta(target);
    }

    /**
     * Get service from env.
     *
     * @param env Environment
     * @return Service
     */
    private getService(env: MarksyncEnv): RemoteService | null {
        const uploader = this.getUploader(env);
        const service = env.SERVICE;
        const serviceName = env.SERVICE_NAME;
        if (service == QiitaService.SERVICE_NAME) {
            const username = env.QIITA_USERNAME;
            const accessToken = env.QIITA_ACCESS_TOKEN;
            if (username !== undefined && accessToken !== undefined) {
                return new QiitaService(
                    serviceName || service,
                    new QiitaApiClient(username, accessToken),
                    uploader
                );
            }
        } else if (service == EsaService.SERVICE_NAME) {
            const team = env["ESA_TEAM"];
            const username = env.ESA_USERNAME;
            const accessToken = env.ESA_ACCESS_TOKEN;
            if (team !== undefined && username !== undefined && accessToken !== undefined) {
                return new EsaService(
                    serviceName || service,
                    new EsaApiClient(team, username, accessToken),
                    uploader
                );
            }
        } else if (service == ZennService.SERVICE_NAME) {
            const username = env["ZENN_USERNAME"];
            const gitDir = env["ZENN_GIT_DIR"];
            const gitUrl = env["ZENN_GIT_URL"];
            const gitBranch = env["ZENN_GIT_BRANCH"];
            const gitUsername = env["ZENN_GIT_USERNAME"];
            const gitPassword = env["ZENN_GIT_PASSWORD"];
            if (username !== undefined && gitDir !== undefined && gitUrl !== undefined &&
                gitBranch !== undefined && gitUsername !== undefined && gitPassword !== undefined) {
                return new ZennService(
                    serviceName || service,
                    new ZennRepository(username, gitDir, gitUrl, gitBranch, gitUsername, gitPassword),
                    uploader
                );
            }
        }
        process.stderr.write("ERROR: invalid configuration.\n");
        return null
    }

    // noinspection JSMethodCanBeStatic
    /**
     * Get uploader from env.
     *
     * @param env Environment
     * @return Uploader
     */
    private getUploader(env: MarksyncEnv): Uploader | null {
        if (env.UPLOADER == "s3") {
            const bucketName = env.S3_BUCKET_NAME;
            const prefix = env.S3_PREFIX;
            const baseUrl = env.S3_BASE_URL;
            if (bucketName && baseUrl) {
                return new S3Uploader(bucketName, prefix, baseUrl);
            }
        } else if (env.SERVICE == "esa") {
            const team = env.ESA_TEAM!
            const username = env.ESA_USERNAME!
            const accessToken = env.ESA_ACCESS_TOKEN!
            if (team && username && accessToken) {
                return new EsaUploader(new EsaApiClient(team, username, accessToken));
            }
        }
        return null;
    }
}
