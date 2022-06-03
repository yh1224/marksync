import axios from "axios";
import * as fs from "fs";
import * as path from "path";

/**
 * Local document class.
 *
 * @param title Document title
 * @param body Document body
 */
export class LocalDocument {
    public static DOCUMENT_FILENAME = "index.md";
    private static HEADER_FILENAME = "head.%s.md";
    private static FOOTER_FILENAME = "foot.%s.md";

    constructor(
        private readonly dirPath: string,
        public readonly title: string,
        public readonly body: string,
    ) {
    }

    /** files in document body */
    private files?: { [name: string]: string }

    getFiles(): { [name: string]: string } {
        if (this.files === undefined) {
            this.files = {};
            for (const line of this.body.split(/(?<=\n)/)) {
                for (const m of line.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
                    const filename = m[1].replace(/%20/g, " ");
                    if (filename.match(/[a-z]+:\/\/.*/)) {
                        /*
                        if (!this.checkURL(filename)) {
                            process.stderr.write(`warning: not accessible publicly: ${filename}\n`);
                        }
                        */
                    } else if (!filename.match(/#.*/)) {
                        let filePath;
                        if (filename.startsWith("/")) {
                            filePath = filename;
                        } else {
                            filePath = path.join(this.dirPath, filename);
                        }
                        if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
                            if (path.basename(filename) === LocalDocument.DOCUMENT_FILENAME) {
                                // link to another document
                                this.files![filename] = path.dirname(filePath);
                            } else {
                                // regular file
                                this.files![filename] = filePath;
                            }
                        } else {
                            process.stderr.write(`warning: local file not exists: ${filePath}\n`);
                        }
                    }
                }
            }
        }
        return this.files!;
    }

    // noinspection JSMethodCanBeStatic
    /**
     * Check URL
     *
     * @param url URL
     * @return URL is accessible or not.
     */
    private async checkURL(url: string): Promise<boolean> {
        try {
            await axios.get(url);
            return true;
        } catch (e) {
            if (!axios.isAxiosError(e)) throw e;
            return false;
        }
    }

    /**
     * Create document from directory.
     *
     * @param dirPath Document directory path
     * @param name name for header/footer
     * @return Document
     */
    static of(dirPath: string, name?: string): LocalDocument {
        let header = "";
        let footer = "";
        if (name !== undefined) {
            const fHeader = path.join(dirPath, LocalDocument.HEADER_FILENAME.replace("%s", name));
            const fFooter = path.join(dirPath, LocalDocument.FOOTER_FILENAME.replace("%s", name));
            if (fs.existsSync(fHeader)) {
                header = fs.readFileSync(fHeader).toString() + "\n";
            }
            if (fs.existsSync(fFooter)) {
                footer = "\n" + fs.readFileSync(fFooter).toString();
            }
        }

        // title と body に分離
        let title = "";
        const content = fs.readFileSync(path.join(dirPath, LocalDocument.DOCUMENT_FILENAME)).toString();
        let bodyBuf = "";
        let comment = false;
        for (const line of content.split(/(?<=\n)/)) {
            if (title !== "") {
                if (line.trim() == "<!--" || line.trim() == "<!---") comment = true;
                if (!comment) bodyBuf += line;
                if (line.trim() == "-->") comment = false;
            } else if (line.match(/^#\s/)) {
                title = line.replace(/^#\s+/, "").trim();
            }
        }
        const body = bodyBuf.replace(/[\r\n]+/, "");
        return new LocalDocument(dirPath, title, header + body + footer);
    }
}