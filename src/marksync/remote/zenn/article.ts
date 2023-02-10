import * as crypto from "crypto";
import * as fs from "fs";
import * as path from "path";
import {UmlUtils} from "../../lib/uml";
import {FileInfo, IFileInfo} from "../../uploader/file";
import {RemoteDocument} from "../document";

export interface IZennArticle {
    readonly slug?: string;
    readonly url?: string;
    readonly type?: string;
    readonly topics?: string[];
    readonly published?: boolean;
    readonly title?: string;
    readonly body?: string;
    readonly files?: { [name: string]: string };
    readonly fileInfoList?: IFileInfo[];
}

export class ZennArticle extends RemoteDocument implements IZennArticle {
    public readonly slug?: string;
    public readonly url?: string;
    public readonly type: string = "";
    public readonly topics: string[] = [];
    public readonly published: boolean = false;
    public readonly title: string = "";
    public readonly body: string = "";
    public readonly files: { [name: string]: string } = {};
    public readonly fileInfoList: FileInfo[] = [];

    constructor(data: IZennArticle) {
        super();
        if (data !== undefined) {
            this.slug = data.slug;
            this.url = data.url;
            if (data.type !== undefined) this.type = data.type;
            if (data.topics !== undefined) this.topics = data.topics;
            if (data.published !== undefined) this.published = data.published;
            if (data.title !== undefined) this.title = data.title;
            if (data.body !== undefined) this.body = data.body;
            if (data.files !== undefined) this.files = data.files;
            if (data.fileInfoList !== undefined) this.fileInfoList = data.fileInfoList.map(it => new FileInfo(it));
        }
    }

    override getDocumentId(): string | null {
        return this.slug !== undefined ? this.slug : null;
    }

    override getDocumentUrl(): string | null {
        return this.url !== undefined ? this.url : null;
    }

    override async getDigest(): Promise<string> {
        const digest = crypto.createHash("sha1");
        [
            this.type,
            this.topics.join(","),
            String(this.published),
            this.getDocumentTitle(),
            await this.getDocumentBody(),
        ].forEach(it => digest.update(Buffer.from(it)));
        return digest.digest("hex");
    }

    override getDocumentTitle(): string {
        return this.title;
    }

    /**
     * Convert markdown to Zenn.
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
            } else if (fUml) {
                umlBody += line;
            } else {
                newBody += line;
            }
        }
        return this.convertFiles(newBody);
    }

    override saveBody(filePath: string): void {
        // write index.md
        fs.writeFileSync(filePath, `# ${this.title}\n\n${this.body}`);
    }

    override async isModified(oldDoc: RemoteDocument, printDiff: boolean = false): Promise<boolean> {
        const oldArticle = oldDoc as ZennArticle
        return [
            this.diff("type", oldArticle.type, this.type, printDiff),
            this.diff("topics", oldArticle.topics, this.topics, printDiff),
            this.diff("published", oldArticle.published, this.published, printDiff),
            this.diff("title", oldArticle.title, this.getDocumentTitle(), printDiff),
            this.diff("body", oldArticle.body, await this.getDocumentBody(), printDiff),
        ].includes(true);
    }
}
