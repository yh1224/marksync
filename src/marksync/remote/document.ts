import * as Diff from "diff";
import {Mapper} from "../lib/mapper";
import {FileInfo} from "../uploader/file";

/**
 * Service specific document
 */
export abstract class RemoteDocument {
    public abstract files: { [name: string]: string };
    public abstract fileInfoList: FileInfo[];

    /**
     * Get document identifier on this service.
     *
     * @return Document identifier
     */
    abstract getDocumentId(): string | null;

    /**
     * Get URL on this service.
     *
     * @return URL
     */
    abstract getDocumentUrl(): string | null;

    /**
     * Get digest.
     *
     * @return
     */
    abstract getDigest(): Promise<string>;

    /**
     * Get document title.
     *
     * @return document title
     */
    abstract getDocumentTitle(): string;

    /**
     * Get body.
     *
     * @return body
     */
    abstract getDocumentBody(): Promise<string>;

    /**
     * Convert image tag to link primary site.
     *
     * @return Converted string
     */
    convertFiles(body: string): string {
        let result = body;
        for (const fileInfo of this.fileInfoList) {
            const filename = fileInfo.filename.replace(/ /g, "%20");
            // https://stackoverflow.com/questions/6318710/javascript-equivalent-of-perls-q-e-or-quotemeta
            const reFilename = filename.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, "\\$&");
            result = result.replace(
                new RegExp(`\\[(.*)\\]\\(${reFilename}\\)`, "g"),
                `[$1](${fileInfo.url})`
            );
        }
        return result;
    }

    /**
     * Save body data to file.
     *
     * @param filePath Target file path
     */
    abstract saveBody(filePath: string): void;

    /**
     * Check modified.
     *
     * @param oldDoc Old document
     * @param printDiff true to print differ
     */
    abstract isModified(oldDoc: RemoteDocument, printDiff?: boolean): Promise<boolean>;

    /**
     * Print diff.
     *
     * @param name Name
     * @param source Source string
     * @param target Target string
     * @param printDiff true to print differ
     * @return true:differ
     */
    diff<T>(name: string, source: T, target: T, printDiff: boolean): boolean {
        const stringify = function (obj: any): string {
            if (typeof obj === "string") {
                if (obj.endsWith("\n")) {
                    return obj.replace(/\r*\n/g, "\n");
                } else {
                    return obj.replace(/\r*\n/g, "\n") + "\n";
                }
            } else {
                return Mapper.getJson(obj) + "\n";
            }
        }
        const differ = stringify(source) !== stringify(target);
        if (differ && printDiff) {
            const patch = Diff.createPatch(name, stringify(source), stringify(target),
                undefined, undefined, {context: 2});
            process.stdout.write(`  [${name}]\n`);
            for (const line of patch.split(/\n/).slice(4)) {
                process.stdout.write(`  ${line}\n`);
            }
        }
        return differ;
    }
}
