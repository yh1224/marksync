import * as fs from "fs";
import * as path from "path";
import {Mapper} from "../../lib/mapper";
import {Uploader} from "../../uploader/uploader";
import {FileInfo} from "../../uploader/file";
import {LocalDocument} from "../../document";
import {RemoteDocument} from "../document";
import {RemoteService} from "../service";
import {ZennArticle} from "./article";
import {ZennDocMeta} from "./meta";
import {ZennRepository} from "./repository";

export class ZennService extends RemoteService {
    static readonly SERVICE_NAME = "zenn"

    private articles: ZennArticle[] | null
    private zennRepository: ZennRepository;

    constructor(serviceName: string, zennRepository: ZennRepository, uploader: Uploader | null = null) {
        super(serviceName, uploader);
        this.zennRepository = zennRepository;
        this.articles = null
    }

    override async getDocuments(): Promise<{ [name: string]: ZennArticle } | null> {
        if (this.articles === null) {
            this.articles = await this.zennRepository.getArticles();
            if (this.articles === null) {
                return null;
            }
        }
        return this.articles.reduce((a, it) => ({...a, [it.slug!]: it}), {});
    }

    override async getDocument(id: string): Promise<RemoteDocument | null> {
        let article = this.articles?.find(it => it.slug == id) || null;
        if (article === null) {
            article = await this.zennRepository.getArticle(id);
        }
        return article;
    }

    override toServiceDocument(doc: LocalDocument, dirPath: string): [ZennArticle, string | null] | null {
        const metaFilePath = path.join(dirPath, this.metaFilename);

        if (fs.existsSync(metaFilePath)) {
            const articleMeta = Mapper.readYamlFile<ZennDocMeta>(metaFilePath);
            return [
                new ZennArticle({
                    slug: articleMeta.slug,
                    url: articleMeta.url,
                    type: articleMeta.type,
                    topics: articleMeta.topics,
                    published: articleMeta.published,
                    title: doc.title,
                    body: doc.body,
                    files: doc.getFiles(),
                    fileInfoList: articleMeta.files,
                }),
                articleMeta.digest || null,
            ];
        } else {
            return null;
        }
    }

    override createMeta(dirPath: string): void {
        const metaFilePath = path.join(dirPath, this.metaFilename);
        if (fs.existsSync(metaFilePath)) {
            process.stdout.write(`${metaFilePath} already exists.\n`);
            return;
        }
        // create new marksync.yml
        Mapper.writeYamlFile(metaFilePath, new ZennDocMeta());
        process.stdout.write(`${metaFilePath} created.\n`);
    }

    override async saveMeta(doc: RemoteDocument, dirPath: string, files: FileInfo[] = []): Promise<void> {
        const article = doc as ZennArticle;
        const metaFilePath = path.join(dirPath, this.metaFilename);

        // update marksync.yml
        Mapper.writeYamlFile(metaFilePath, new ZennDocMeta({
            slug: article.slug,
            url: article.url,
            digest: await article.getDigest(),
            type: article.type,
            topics: article.topics,
            published: article.published,
            files: files,
        }));
    }

    override async update(doc: RemoteDocument, message: string | null): Promise<RemoteDocument | null> {
        return await this.zennRepository.saveArticle(doc as ZennArticle, message);
    }
}
