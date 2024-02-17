import * as fs from "fs";
import nodegit from "nodegit";
import * as os from "os";
import * as path from "path";
import {Mapper} from "../../lib/mapper";
import {ZennArticle} from "./article";

class IZennArticleMeta {
    readonly type?: string;
    readonly topics?: string[];
    readonly published?: boolean;
    readonly publication_name?: string;
    readonly title?: string;
}

class ZennArticleMeta {
    public readonly type: string = "";
    public readonly topics: string[] = [];
    public readonly published: boolean = false;
    public readonly publication_name?: string;
    public readonly title: string = "";

    constructor(data: IZennArticleMeta) {
        if (data.type !== undefined) this.type = data.type;
        if (data.topics !== undefined) this.topics = data.topics;
        if (data.published !== undefined) this.published = data.published;
        if (data.title !== undefined) this.title = data.title;
    }
}

/**
 * Repository for Zenn GitHub
 */
export class ZennRepository {
    static readonly ARTICLES_PATH = "articles";

    private readonly credentials;
    private workDir?: string;
    private repository?: nodegit.Repository;

    constructor(
        private readonly username: string,
        private readonly gitDir: string,
        private readonly gitUrl: string,
        private readonly gitBranch: string,
        private readonly gitUsername: string,
        private readonly gitPassword: string
    ) {
        this.credentials = (url: string, username: string) => nodegit.Cred.userpassPlaintextNew(username, this.gitPassword);
    }

    private getWorkDir(): string {
        if (!this.workDir) {
            const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "marksync-zenn"));
            process.on("exit", () => fs.rmSync(tmpDir, {recursive: true}));
            this.workDir = tmpDir;
        }
        return this.workDir;
    }

    private async getRepository(): Promise<nodegit.Repository | null> {
        if (!this.repository) {
            try {
                if (fs.existsSync(this.gitDir) && fs.statSync(this.gitDir).isDirectory()) {
                    this.repository = await nodegit.Repository.openBare(this.gitDir);
                    process.stderr.write(`# fetch from ${this.gitUrl}\n`);
                    await this.repository.fetchAll({callbacks: {credentials: this.credentials}});
                } else {
                    fs.mkdirSync(path.dirname(this.gitDir), {recursive: true});
                    process.stderr.write(`# clone from ${this.gitUrl}\n`);
                    this.repository = await nodegit.Clone(this.gitUrl, this.gitDir, {
                        bare: 1,
                        fetchOpts: {callbacks: {credentials: this.credentials}},
                    });
                }
                this.repository!.setWorkdir(this.getWorkDir(), 0);
            } catch (e) {
                process.stderr.write(`${e}\n`);
                return null;
            }
        }
        return this.repository;
    }

    /**
     * List article slugs.
     */
    private async listArticleSlugs(): Promise<string[] | null> {
        const repos = await this.getRepository();
        if (repos === null) {
            return null;
        }
        try {
            const firstCommit = await repos.getBranchCommit(this.gitBranch);
            const tree = await firstCommit.getTree();
            const articles = await tree.getEntry("articles");
            const entries = (await articles.getTree()).entries();
            const files: string[] = [];
            for (const entry of entries) {
                const m = entry.path().match(/^articles\/([\da-z]+)\.md$/);
                if (m) {
                    files.push(m[1]);
                }
            }
            return files;
        } catch (e) {
            process.stderr.write(`${e}\n`);
            return null;
        }
    }

    /**
     * Get articles.
     */
    async getArticles(): Promise<ZennArticle[] | null> {
        const result = [];
        const slugs = await this.listArticleSlugs();
        if (slugs === null) {
            return null;
        }
        for (const slug of slugs) {
            const article = await this.getArticle(slug);
            if (article === null) {
                return null;
            }
            result.push(article);
        }
        return result;
    }

    /**
     * Get article.
     */
    async getArticle(slug: string): Promise<ZennArticle | null> {
        const fileName = `${ZennRepository.ARTICLES_PATH}/${slug}.md`
        const filePath = path.join(this.getWorkDir(), fileName);
        const repos = await this.getRepository();
        if (repos === null) {
            return null;
        }
        await repos.checkoutBranch(this.gitBranch, {
            targetDirectory: this.workDir,
            paths: [fileName],
        });
        if (!fs.existsSync(filePath)) {
            process.stderr.write(`ERROR: failed to get article ${slug}. (not exists)\n`);
            return null;
        }

        const contents = fs.readFileSync(filePath).toString().split("---\n");
        if (contents.length < 3) {
            process.stderr.write(`ERROR: failed to get article ${slug}. (invalid contents)\n`);
            return null;
        }
        const [_, metaYaml] = contents;
        const body = contents.slice(2).join("---\n");
        const meta = Mapper.readYaml<ZennArticleMeta>(metaYaml);
        return new ZennArticle({
            slug: slug,
            url: this.zennUrl(slug),
            type: meta.type,
            topics: meta.topics,
            published: meta.published,
            publication_name: meta.publication_name,
            title: meta.title,
            body: body,
        });
    }

    /**
     * Save article.
     */
    async saveArticle(article: ZennArticle, message: string | null): Promise<ZennArticle | null> {
        const slug = article.slug || this.newSlug();

        const fileName = `${ZennRepository.ARTICLES_PATH}/${slug}.md`;
        const filePath = path.join(this.getWorkDir(), fileName);
        const repos = await this.getRepository();
        if (repos === null) {
            return null;
        }
        try {
            await repos.checkoutBranch(this.gitBranch, {
                targetDirectory: this.workDir,
                // paths: [fileName],
            });
        } catch (e) {
            process.stderr.write(`${e}\n`);
            return null;
        }
        if (!fs.existsSync(filePath)) {
            fs.mkdirSync(path.dirname(filePath), {recursive: true});
        }

        const frontMatter: { [name: string]: any } = {
            title: article.title,
            type: article.type,
            topics: article.topics,
            published: article.published,
        };
        if (article.publication_name) {
            frontMatter.publication_name = article.publication_name;
        }
        const content = "---\n" +
            Mapper.toYaml(frontMatter) +
            "---\n" +
            await article.getDocumentBody();
        fs.writeFileSync(filePath, content);

        try {
            const index = await repos.refreshIndex();
            await index.addByPath(fileName);
            await index.write();
            const oid = await index.writeTree();
            const parent = await repos.getHeadCommit();
            await repos.createCommit("HEAD",
                await nodegit.Signature.default(repos), await nodegit.Signature.default(repos),
                message || `update ${slug}`, oid, [parent]);

            process.stderr.write(`# push to ${this.gitUrl}\n`);
            const remote = await repos.getRemote("origin");
            await remote.push([`refs/heads/${this.gitBranch}:refs/heads/${this.gitBranch}`], {
                callbacks: {credentials: this.credentials},
            });
        } catch (e) {
            process.stderr.write(`${e}\n`);
            return null;
        }

        return new ZennArticle({
            ...article,
            slug: slug,
            url: this.zennUrl(slug),
        });
    }

    /**
     * Get zenn URL.
     */

    private zennUrl(slug: string): string {
        return `https://zenn.dev/${this.username}/articles/${slug}`;
    }

    // noinspection JSMethodCanBeStatic
    /**
     * Generate new slug.
     */
    private newSlug(): string {
        const allowedChars = "0123456789abcdefghijklmnopqrstuvwxyz";
        let result = "";
        for (let i = 0; i < 17; i++) {
            result += allowedChars.charAt(Math.floor(Math.random() * 17));
        }
        return result;
    }
}
