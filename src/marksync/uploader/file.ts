import * as crypto from "crypto";
import * as fs from "fs";
import * as path from "path";

export interface IFileInfo {
    readonly filename?: string;
    readonly digest?: string;
    readonly url?: string;
}

export class FileInfo {
    public readonly filename: string;
    public readonly digest?: string;
    public readonly url?: string;

    constructor(data: IFileInfo) {
        this.filename = data.filename!;
        this.digest = data.digest;
        this.url = data.url;
    }

    static of(filename: string, url: string): FileInfo {
        return new FileInfo({
            filename: filename,
            digest: FileInfo.sha1(filename),
            url: url,
        });
    }

    /**
     * Check if this file is not modified.
     */
    isIdenticalTo(filePath: string): boolean {
        return this.digest == FileInfo.sha1(filePath);
    }

    /**
     * Calculate file digest.
     */
    static sha1(filePath: string): string {
        const digest = crypto.createHash("sha1");
        digest.update(fs.readFileSync(filePath));
        return digest.digest("hex");
    }
}
