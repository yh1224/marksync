import * as crypto from "crypto";
import * as fs from "fs";
import {RemoteDocument} from "../document";
import {FileInfo, IFileInfo} from "../../uploader/file";

export interface IEsaPost {
    readonly number?: number;
    readonly url?: string;
    readonly created_at?: string;
    readonly updated_at?: string;
    readonly category?: string;
    readonly tags?: string[];
    readonly wip?: boolean;
    readonly body_md?: string;
    readonly name?: string;
    readonly files?: { [name: string]: string };
    readonly fileInfoList?: IFileInfo[];
}

export class EsaPost extends RemoteDocument {
    public readonly number?: number;
    public readonly url?: string;
    public readonly created_at?: string;
    public readonly updated_at?: string;
    public readonly category?: string;
    public readonly tags: string[] = [];
    public readonly wip: boolean = true;
    public readonly body_md: string = "";
    public readonly name: string = "";
    public override readonly files: { [name: string]: string } = {};
    public override readonly fileInfoList: FileInfo[] = [];

    constructor(data?: IEsaPost) {
        super();
        if (data !== undefined) {
            this.number = data.number;
            this.url = data.url;
            this.created_at = data.created_at;
            this.updated_at = data.updated_at;
            this.category = data.category;
            if (data.tags !== undefined) this.tags = data.tags;
            if (data.wip !== undefined) this.wip = data.wip;
            if (data.body_md !== undefined) this.body_md = data.body_md;
            if (data.name !== undefined) this.name = data.name;
            if (data.files !== undefined) this.files = data.files;
            if (data.fileInfoList !== undefined) this.fileInfoList = data.fileInfoList.map(it => new FileInfo(it));
        }
    }

    override getDocumentId(): string | null {
        return this.number !== undefined ? String(this.number) : null;
    }

    override getDocumentUrl(): string | null {
        return this.url !== undefined ? this.url : null;
    }

    override async getDigest(): Promise<string> {
        const digest = crypto.createHash("sha1");
        [
            this.category || "",
            this.tags.join(","),
            String(this.wip),
            this.getDocumentTitle(),
            await this.getDocumentBody(),
        ].forEach(it => digest.update(Buffer.from(it)));
        return digest.digest("hex");
    }

    override getDocumentTitle(): string {
        return this.name.replace(/\//g, "∕");
    }

    override async getDocumentBody(): Promise<string> {
        let newBody = "";
        for (const line of this.body_md.split(/(?<=\n)/)) {
            const trimmedLine = line.trim();
            if (trimmedLine == "```plantuml") {
                newBody += line.replace(/^```plantuml/, "```uml");
            } else if (trimmedLine == "```puml") {
                newBody += line.replace(/^```puml/, "```uml");
            } else {
                newBody += line;
            }
        }
        return this.convertFiles(newBody);
    }

    override saveBody(filePath: string): void {
        // write index.md
        fs.writeFileSync(filePath, `# ${this.name}\n\n${this.body_md}`);
    }

    override async isModified(oldDoc: RemoteDocument, printDiff: boolean = false): Promise<boolean> {
        const oldPost = oldDoc as EsaPost
        return [
            this.diff("category", oldPost.category, this.category, printDiff),
            this.diff("tags", oldPost.tags, this.tags, printDiff),
            this.diff("wip", oldPost.wip, this.wip, printDiff),
            this.diff("title", oldPost.name, this.getDocumentTitle(), printDiff),
            this.diff("body", oldPost.body_md, await this.getDocumentBody(), printDiff),
        ].includes(true);
    }
}
