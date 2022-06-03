import * as fs from "fs";
import * as path from "path";
import {Mapper} from "../../lib/mapper";
import {FileInfo} from "../../uploader/file";
import {Uploader} from "../../uploader/uploader";
import {LocalDocument} from "../../document";
import {RemoteDocument} from "../document";
import {RemoteService} from "../service";
import {QiitaApiClient} from "./api";
import {QiitaItem} from "./item";
import {IQiitaItemMeta, QiitaItemMeta} from "./meta";

export class QiitaService extends RemoteService {
    static readonly SERVICE_NAME = "qiita"

    private apiClient: QiitaApiClient;
    private items: QiitaItem[] | null = null;

    constructor(serviceName: string, apiClient: QiitaApiClient, uploader: Uploader | null = null) {
        super(serviceName, uploader);
        this.apiClient = apiClient;
    }

    override async getDocuments(): Promise<{ [name: string]: QiitaItem } | null> {
        if (this.items === null) {
            this.items = await this.apiClient.getItems();
            if (this.items === null) {
                return null;
            }
        }
        return this.items.reduce((a, it) => ({...a, [it.id!]: it}), {});
    }

    override async getDocument(id: string): Promise<RemoteDocument | null> {
        let item = this.items?.find(it => it.id == id) || null;
        if (item === null) {
            item = await this.apiClient.getItem(id);
        }
        return item;
    }

    override toServiceDocument(doc: LocalDocument, dirPath: string): [QiitaItem, string | null] | null {
        const metaFilePath = path.join(dirPath, this.metaFilename);

        if (fs.existsSync(metaFilePath)) {
            const itemMeta = Mapper.readYamlFile<IQiitaItemMeta>(metaFilePath);
            return [
                new QiitaItem({
                    id: itemMeta.id,
                    url: itemMeta.url,
                    created_at: itemMeta.created_at,
                    updated_at: itemMeta.updated_at,
                    tags: itemMeta.tags,
                    private: itemMeta.private,
                    body: doc.body,
                    title: doc.title,
                    files: doc.getFiles(),
                    fileInfoList: itemMeta.files,
                }),
                itemMeta.digest || null
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
        Mapper.writeYamlFile(metaFilePath, new QiitaItemMeta());
        process.stdout.write(`${metaFilePath} created.\n`);
    }

    override async saveMeta(doc: RemoteDocument, dirPath: string, files: FileInfo[] = []): Promise<void> {
        const item = doc as QiitaItem;
        const metaFilePath = path.join(dirPath, this.metaFilename);

        // update marksync.yml
        Mapper.writeYamlFile(metaFilePath, new QiitaItemMeta({
            id: item.id,
            url: item.url,
            created_at: item.created_at,
            updated_at: item.updated_at,
            digest: await item.getDigest(),
            tags: item.tags,
            private: item.private,
            files: files,
        }));
    }

    override async update(doc: RemoteDocument, message: string | null): Promise<RemoteDocument | null> {
        if (message != null) {
            process.stderr.write(`WARNING: update message ignored: ${message}\n`);
        }
        return await this.apiClient.saveItem(doc as QiitaItem);
    }
}
