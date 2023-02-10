import {FileInfo} from "../uploader/file";

export interface IRemoteMeta {
    readonly digest?: string;
    readonly files?: FileInfo[];
}

export class RemoteMeta {
    readonly digest?: string;
    readonly files?: FileInfo[];

    constructor(data?: IRemoteMeta) {
        if (data !== undefined) {
            this.digest = data.digest;
            if (data.files !== undefined) this.files = data.files;
        }
    }
}
