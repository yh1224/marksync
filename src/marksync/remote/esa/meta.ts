import {IRemoteMeta, RemoteMeta} from "../meta";

export interface IEsaPostMeta extends IRemoteMeta {
    readonly number?: number;
    readonly url?: string;
    readonly created_at?: string;
    readonly updated_at?: string;
    readonly category?: string;
    readonly tags?: string[];
    readonly wip?: boolean;
}

export class EsaPostMeta extends RemoteMeta implements IEsaPostMeta {
    public readonly number?: number;
    public readonly url?: string;
    public readonly created_at?: string;
    public readonly updated_at?: string;
    public readonly category?: string;
    public readonly tags: string[] = [];
    public readonly wip: boolean = true;

    constructor(data?: IEsaPostMeta) {
        super(data);
        if (data !== undefined) {
            this.number = data.number;
            this.url = data.url;
            this.created_at = data.created_at;
            this.updated_at = data.updated_at;
            this.category = data.category;
            if (data.tags !== undefined) this.tags = data.tags;
            if (data.wip !== undefined) this.wip = data.wip;
        }
    }
}
