import {IRemoteMeta, RemoteMeta} from "../meta";

export interface IZennDocMeta extends IRemoteMeta {
    readonly slug?: string;
    readonly url?: string;
    readonly type?: string;
    readonly topics?: string[];
    readonly published?: boolean;
}

export class ZennDocMeta extends RemoteMeta implements IZennDocMeta {
    public readonly slug?: string;
    public readonly url?: string;
    public readonly digest?: string;
    public readonly type: string = "tech";
    public readonly topics: string[] = [];
    public readonly published: boolean = false;

    constructor(data?: IZennDocMeta) {
        super(data);
        if (data !== undefined) {
            this.slug = data.slug;
            this.url = data.url;
            if (data.type !== undefined) this.type = data.type;
            if (data.topics !== undefined) this.topics = data.topics;
            if (data.published !== undefined) this.published = data.published;
        }
    }
}
