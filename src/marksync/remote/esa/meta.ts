import {FileInfo} from "../../uploader/file";

export interface IEsaPostMeta {
    readonly number?: number;
    readonly url?: string;
    readonly created_at?: string;
    readonly updated_at?: string;
    readonly digest?: string;
    readonly category?: string;
    readonly tags?: string[];
    readonly wip?: boolean;
    readonly files?: FileInfo[];
}

export class EsaPostMeta {
    public readonly number?: number;
    public readonly url?: string;
    public readonly created_at?: string;
    public readonly updated_at?: string;
    public readonly digest?: string;
    public readonly category?: string;
    public readonly tags: string[] = [];
    public readonly wip: boolean = true;
    public readonly files: FileInfo[] = [];

    constructor(data?: EsaPostMeta) {
        if (data !== undefined) {
            this.number = data.number;
            this.url = data.url;
            this.created_at = data.created_at;
            this.updated_at = data.updated_at;
            this.digest = data.digest;
            this.category = data.category;
            if (data.tags !== undefined) this.tags = data.tags;
            if (data.wip !== undefined) this.wip = data.wip;
            if (data.files !== undefined) this.files = data.files;
        }
    }
}
