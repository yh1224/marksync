import * as fs from "fs";
import * as path from "path";
import {Mapper} from "../../lib/mapper";
import {FileInfo} from "../../uploader/file";
import {Uploader} from "../../uploader/uploader";
import {LocalDocument} from "../../document";
import {RemoteDocument} from "../document";
import {RemoteService} from "../service";
import {EsaApiClient} from "./api";
import {EsaPostMeta} from "./meta";
import {EsaPost} from "./post";

export class EsaService extends RemoteService {
    static readonly SERVICE_NAME = "esa"

    private apiClient: EsaApiClient;
    private posts: EsaPost[] | null = null

    constructor(serviceName: string, apiClient: EsaApiClient, uploader: Uploader | null = null) {
        super(serviceName, uploader);
        this.apiClient = apiClient;
    }

    override async getDocuments(): Promise<{ [name: string]: EsaPost } | null> {
        if (this.posts === null) {
            this.posts = await this.apiClient.getPosts();
            if (this.posts === null) {
                return null;
            }
        }
        return this.posts.reduce((a, it) => ({...a, [it.number!]: it}), {});
    }

    override async getDocument(id: string): Promise<RemoteDocument | null> {
        let post = this.posts?.find(it => it.number == Number(id)) || null;
        if (post === null) {
            post = await this.apiClient.getPost(Number(id));
        }
        return post;
    }

    override toServiceDocument(doc: LocalDocument, dirPath: string): [EsaPost, string | null] | null {
        const metaFilePath = path.join(dirPath, this.metaFilename);

        if (fs.existsSync(metaFilePath)) {
            const postMeta = Mapper.readYamlFile<EsaPostMeta>(metaFilePath);
            return [
                new EsaPost({
                    number: postMeta.number,
                    url: postMeta.url,
                    created_at: postMeta.created_at,
                    updated_at: postMeta.updated_at,
                    category: postMeta.category,
                    tags: postMeta.tags,
                    wip: postMeta.wip,
                    name: doc.title,
                    body_md: doc.body,
                    files: doc.getFiles(),
                    fileInfoList: postMeta.files,
                }),
                postMeta.digest || null,
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
        Mapper.writeYamlFile(metaFilePath, new EsaPostMeta());
        process.stdout.write(`${metaFilePath} created.\n`);
    }

    override async saveMeta(doc: RemoteDocument, dirPath: string, files: FileInfo[] = []): Promise<void> {
        const post = doc as EsaPost;
        const metaFilePath = path.join(dirPath, this.metaFilename);

        // update marksync.yml
        Mapper.writeYamlFile(metaFilePath, new EsaPostMeta({
            number: post.number,
            url: post.url,
            created_at: post.created_at,
            updated_at: post.updated_at,
            digest: await post.getDigest(),
            category: post.category,
            tags: post.tags,
            wip: post.wip,
            files: files
        }));
    }

    override async update(doc: RemoteDocument, message: string | null): Promise<RemoteDocument | null> {
        return await this.apiClient.savePost(doc as EsaPost, message);
    }
}
