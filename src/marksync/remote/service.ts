import * as fs from "fs";
import {FileInfo} from "../uploader/file";
import {Uploader} from "../uploader/uploader";
import {LocalDocument} from "../document";
import {RemoteDocument} from "./document";

export abstract class RemoteService {
    static METAFILE_PREFIX = "marksync"

    protected readonly metaFilename: string;

    protected constructor(
        protected readonly serviceName: string,
        private readonly uploader: Uploader | null
    ) {
        this.metaFilename = `${RemoteService.METAFILE_PREFIX}.${serviceName}.yml`
    }

    /**
     * Get all documents.
     *
     * @return Documents by keys
     */
    abstract getDocuments(): Promise<{ [name: string]: RemoteDocument } | null>;

    /**
     * Get one document.
     *
     * @param id Document identifier
     * @return Document
     */
    abstract getDocument(id: string): Promise<RemoteDocument | null>;

    /**
     * Get service document from Document.
     *
     * @param doc Document
     * @param dirPath Document directory path
     * @return ServiceDocument object and expect digest
     */
    abstract toServiceDocument(doc: LocalDocument, dirPath: string): [RemoteDocument, string | null] | null;

    /**
     * Create meta data for service.
     *
     * @param dirPath Document directory path
     */
    abstract createMeta(dirPath: string): void;

    /**
     * Save meta data to file.
     *
     * @param doc Document
     * @param dirPath Document directory path
     * @param files Files
     */
    abstract saveMeta(doc: RemoteDocument, dirPath: string, files?: FileInfo[]): void;

    /**
     * Update document to service.
     *
     * @param doc ServiceDocument object to update
     * @param message update message
     * @return Updated ServiceDocument object
     */
    abstract update(doc: RemoteDocument, message: string | null): Promise<RemoteDocument | null>;

    /**
     * Prefetch documents.
     */
    async prefetch(): Promise<void> {
        await this.getDocuments();
    }

    /**
     * Sync documents to service.
     *
     * @param dirPath Target directory path
     * @param force Force update
     * @param message update message
     * @param checkOnly Check only
     * @param showDiff Show diff
     */
    async sync(dirPath: string, force: boolean, message: string | null, checkOnly: boolean, showDiff: boolean): Promise<void> {
        const target = dirPath;
        const newDocRes = this.toServiceDocument(LocalDocument.of(dirPath, this.serviceName), dirPath);
        if (!newDocRes) return;
        const [newDoc, expectDigest] = newDocRes;

        // check
        let doSync = false;
        const docId = newDoc.getDocumentId();
        if (docId !== null) {
            const oldDoc = await this.getDocument(docId);
            if (oldDoc === null) {
                process.stdout.write(`? ${target}: (${docId}) failed.\n`);
            } else {
                const modifiedFiles = Object.keys(newDoc.files).filter(filename => {
                    const file = newDoc.files[filename];
                    return fs.statSync(file).isFile()
                        && !(newDoc.fileInfoList.find(it => it.filename == filename)?.isIdenticalTo(file) || false);
                });
                const modified = await newDoc.isModified(oldDoc) || modifiedFiles.length > 0;
                const drifted = expectDigest !== null && await oldDoc.getDigest() !== expectDigest;
                if (!modified && !drifted) {
                    process.stdout.write(`  ${target}: not modified.\n`);
                } else {
                    if (drifted) {
                        process.stdout.write(`x ${target}: modified externally.\n`);
                        process.stdout.write(`  ${expectDigest}:${await oldDoc.getDigest()}\n`);
                    } else {
                        process.stdout.write(`! ${target}\n`);
                    }
                    if (!drifted || force) {
                        doSync = true
                    }
                    if (showDiff) {
                        await newDoc.isModified(oldDoc, true);
                        if (modifiedFiles.length > 0) {
                            process.stdout.write("  [files]\n");
                            modifiedFiles.forEach(it => {
                                process.stdout.write(`  !${it}\n`);
                            });
                        }
                        process.stdout.write("  ----------------------------------------------------------------\n");
                    }
                }
            }
        } else {
            process.stdout.write(`+ ${target}\n`);
            doSync = true;
        }

        // sync
        if (doSync && !checkOnly) {
            const newFileInfoList: FileInfo[] = [];
            for (const filename of Object.keys(newDoc.files)) {
                const filePath = newDoc.files[filename];
                const fileInfo = newDoc.fileInfoList.find(it => it.filename == filename);
                if (fs.statSync(filePath).isDirectory()) {
                    // link to another document
                    const anotherDocRes = this.toServiceDocument(LocalDocument.of(filePath, this.serviceName), filePath);
                    if (anotherDocRes !== null) {
                        const url = anotherDocRes[0].getDocumentUrl();
                        if (url !== null) {
                            newFileInfoList.push(new FileInfo({filename, url}));
                        }
                    }
                } else if (fs.statSync(filePath).isFile() && this.uploader !== null) {
                    if (fileInfo?.isIdenticalTo(filePath) != true) {
                        // upload file
                        const url = await this.uploader.upload(filePath);
                        if (url === null) {
                            process.stdout.write(`  ->upload failed. ${filename}\n`);
                            return;
                        }
                        newFileInfoList.push(FileInfo.of(filePath, url));
                        process.stdout.write(`  ->uploaded. ${filename} to ${url}\n`);
                    } else {
                        newFileInfoList.push(fileInfo);
                    }
                }
            }

            newDoc.fileInfoList = newFileInfoList;

            // update document
            const updatedDoc = await this.update(newDoc, message);
            if (updatedDoc !== null) {
                process.stdout.write(`  ->updated. ${updatedDoc.getDocumentUrl()}\n`);
                await this.saveMeta(updatedDoc, dirPath, newDoc.fileInfoList);
            } else {
                process.stdout.write("  ->failed.\n");
            }
        }
    }
}
