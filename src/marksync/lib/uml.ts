import * as crypto from "crypto";
import * as fs from "fs";
// @ts-ignore
import * as plantuml from "node-plantuml";
import * as os from "os";
import * as path from "path";

export class UmlUtils {
    private static TMP_DIR_PREFIX = "marksync-uml.";
    private static TMP_FILENAME_PREFIX = ".uml:";

    /**
     * Convert UML tag to URL.
     *
     * @param umlBody UML string
     * @return Converted string
     */
    static async convertToUrl(umlBody: string): Promise<string> {
        const uml = `@startuml\n${umlBody}\n@enduml\n`;
        const enc = plantuml.encode(uml);
        const chunks = [];
        for await (const chunk of enc.out) {
            chunks.push(Buffer.from(chunk));
        }
        const encodedUml = Buffer.concat(chunks).toString("utf-8");
        return `http://www.plantuml.com/plantuml/svg/${encodedUml}`;
    }

    /**
     * Convert UML tag to PNG image file.
     *
     * @param umlBody UML string
     * @return Converted file
     */
    static async convertToPng(umlBody: string): Promise<string> {
        const dir = fs.mkdtempSync(path.join(os.tmpdir(), UmlUtils.TMP_DIR_PREFIX));
        process.on("exit", () => fs.rmSync(dir, {recursive: true}));
        const uml = `@startuml\n${umlBody}\n@enduml\n`;
        const filePath = path.join(dir, this.pngFilename(uml));
        const gen = plantuml.generate(uml, {format: "png"});
        gen.out.pipe(fs.createWriteStream(filePath));
        for await (const chunk of gen.out) {
        }
        return filePath;
    }

    /**
     * Generate png filename.
     *
     * @param uml UML string
     */
    private static pngFilename(uml: string): string {
        const digest = crypto.createHash("sha1");
        digest.update(Buffer.from(uml));
        return UmlUtils.TMP_FILENAME_PREFIX + digest.digest("hex") + ".png";
    }
}
