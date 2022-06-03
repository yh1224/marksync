import {FileInfo, IFileInfo} from "../../uploader/file";
import {IQiitaItemTag, QiitaItemTag} from "./tag";

export interface IQiitaItemMeta {
    readonly id?: string;
    readonly url?: string;
    readonly created_at?: string;
    readonly updated_at?: string;
    readonly digest?: string;
    readonly tags?: IQiitaItemTag[];
    readonly private?: boolean;
    readonly files?: IFileInfo[];
}

export class QiitaItemMeta {
    public readonly id?: string;
    public readonly url?: string;
    public readonly created_at?: string;
    public readonly updated_at?: string;
    public readonly digest?: string;
    public readonly tags: QiitaItemTag[] = [];
    public readonly private: boolean = true;
    public readonly files: FileInfo[] = [];

    constructor(data?: IQiitaItemMeta) {
        if (data !== undefined) {
            this.id = data.id;
            this.url = data.url;
            this.created_at = data.created_at;
            this.updated_at = data.updated_at;
            this.digest = data.digest;
            if (data.tags !== undefined) this.tags = data.tags.map(it => new QiitaItemTag(it));
            if (data.private !== undefined) this.private = data.private;
            if (data.files !== undefined) this.files = data.files.map(it => new FileInfo(it));
        }
    }
}
