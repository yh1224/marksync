import {IRemoteMeta, RemoteMeta} from "../meta";
import {IQiitaItemTag, QiitaItemTag} from "./tag";

export interface IQiitaItemMeta extends IRemoteMeta {
    readonly id?: string;
    readonly url?: string;
    readonly created_at?: string;
    readonly updated_at?: string;
    readonly tags?: IQiitaItemTag[];
    readonly private?: boolean;
}

export class QiitaItemMeta extends RemoteMeta implements IQiitaItemMeta {
    public readonly id?: string;
    public readonly url?: string;
    public readonly created_at?: string;
    public readonly updated_at?: string;
    public readonly tags: QiitaItemTag[] = [];
    public readonly private: boolean = true;

    constructor(data?: IQiitaItemMeta) {
        super(data);
        if (data !== undefined) {
            this.id = data.id;
            this.url = data.url;
            this.created_at = data.created_at;
            this.updated_at = data.updated_at;
            if (data.tags !== undefined) this.tags = data.tags.map(it => new QiitaItemTag(it));
            if (data.private !== undefined) this.private = data.private;
        }
    }
}
