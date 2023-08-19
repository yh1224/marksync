import * as crypto from "crypto";
import * as fs from "fs";
import * as path from "path";
import {UmlUtils} from "../../lib/uml";
import {FileInfo, IFileInfo} from "../../uploader/file";
import {RemoteDocument} from "../document";
import {IQiitaItemTag, QiitaItemTag} from "./tag";

export interface IQiitaItem {
    readonly id?: string;
    readonly url?: string;
    readonly created_at?: string;
    readonly updated_at?: string;
    readonly tags?: IQiitaItemTag[];
    readonly "private"?: boolean;
    readonly body?: string;
    readonly title?: string;
    readonly files?: { [name: string]: string };
    readonly fileInfoList?: IFileInfo[];
}

export class QiitaItem extends RemoteDocument implements IQiitaItem {
    public readonly id?: string;
    public readonly url?: string;
    public readonly created_at?: string;
    public readonly updated_at?: string;
    public readonly tags: QiitaItemTag[] = [];
    public readonly "private": boolean = true;
    public readonly body: string = "";
    public readonly title: string = "";
    public readonly files: { [name: string]: string } = {};
    public readonly fileInfoList: FileInfo[] = [];

    constructor(data?: IQiitaItem) {
        super();
        if (data !== undefined) {
            this.id = data.id;
            this.url = data.url;
            this.created_at = data.created_at;
            this.updated_at = data.updated_at;
            if (data.tags !== undefined) this.tags = data.tags.map(it => new QiitaItemTag(it));
            if (data.private !== undefined) this.private = data.private;
            if (data.body !== undefined) this.body = data.body;
            if (data.title !== undefined) this.title = data.title;
            if (data.files !== undefined) this.files = data.files;
            if (data.fileInfoList !== undefined) this.fileInfoList = data.fileInfoList.map(it => new FileInfo(it));
        }
    }

    override getDocumentId(): string | null {
        return this.id !== undefined ? this.id : null;
    }

    override getDocumentUrl(): string | null {
        return this.url !== undefined ? this.url : null;
    }

    override async getDigest(): Promise<string> {
        const digest = crypto.createHash("sha1");
        [
            this.tags.map(it => it.toString()).join(","),
            String(this.private),
            this.getDocumentTitle(),
            await this.getDocumentBody(),
        ].forEach(it => digest.update(Buffer.from(it)));
        return digest.digest("hex");
    }

    override getDocumentTitle(): string {
        return this.title;
    }

    /**
     * Convert markdown to Qiita.
     *
     * @return body
     */
    override async getDocumentBody(): Promise<string> {
        let newBody = "";
        let fUml = false;
        let umlBody = "";
        for (const line of this.body.split(/(?<=\n)/)) {
            // convert uml
            const trimmedLine = line.trim();
            if (trimmedLine == "```plantuml" || trimmedLine == "```puml" || trimmedLine == "```uml") {
                fUml = true;
            } else if (fUml && trimmedLine == "```") {
                const pngFilePath = await UmlUtils.convertToPng(umlBody);
                const pngFileName = path.basename(pngFilePath);
                if (!(pngFileName in this.files)) {
                    this.files[pngFileName] = pngFilePath;
                }
                newBody += `![](${pngFileName})\n`;
                fUml = false;
                umlBody = "";
            } else if (fUml) {
                umlBody += line;
            } else {
                newBody += line;
            }
        }
        return this.convertFiles(newBody);
    }

    override saveBody(filePath: string) {
        // write index.md
        fs.writeFileSync(filePath, `# ${this.title}\n\n${this.body}`);
    }

    override async isModified(oldDoc: RemoteDocument, printDiff: boolean = false): Promise<boolean> {
        const oldItem = oldDoc as QiitaItem
        return [
            this.diff("tags", oldItem.tags, this.tags, printDiff),
            this.diff("private", oldItem.private, this.private, printDiff),
            this.diff("title", oldItem.title, this.getDocumentTitle(), printDiff),
            this.diff("body", oldItem.body, await this.getDocumentBody(), printDiff),
        ].includes(true);
    }
}
