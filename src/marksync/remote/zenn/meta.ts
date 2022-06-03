import {FileInfo, IFileInfo} from "../../uploader/file";

export interface IZennDocMeta {
    readonly slug?: string;
    readonly url?: string;
    readonly digest?: string;
    readonly type?: string;
    readonly topics?: string[];
    readonly published?: boolean;
    readonly files?: IFileInfo[];
}

export class ZennDocMeta {
    public readonly slug?: string;
    public readonly url?: string;
    public readonly digest?: string;
    public readonly type: string = "tech";
    public readonly topics: string[] = [];
    public readonly published: boolean = false;
    public readonly files: FileInfo[] = [];

    constructor(data?: IZennDocMeta) {
        if (data !== undefined) {
            this.slug = data.slug;
            this.url = data.url;
            this.digest = data.digest;
            if (data.type !== undefined) this.type = data.type;
            if (data.topics !== undefined) this.topics = data.topics;
            if (data.published !== undefined) this.published = data.published;
            if (data.files !== undefined) this.files = data.files.map(it => new FileInfo(it));
        }
    }
}
