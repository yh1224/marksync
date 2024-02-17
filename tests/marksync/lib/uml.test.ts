import * as fs from "fs";
import * as path from "path";
import {UmlUtils} from "../../../src/marksync/lib/uml";

describe("UmlUtils", () => {
    test("convertToUrl", async () => {
        const result = await UmlUtils.convertToUrl("a -> b: foo\na <-- b");
        expect(result).toBe("http://www.plantuml.com/plantuml/svg/SoWkIImgAStDuKfKqBLJIB9IIClFvqfKiD7LLKZYSaZDIm790G00");
    });

    test("convertToTempFile", async () => {
        const result = await UmlUtils.convertToPng("a -> b: foo\na <-- b");
        expect(fs.statSync(result).isFile()).toBeTruthy();
        expect(path.basename(result)).toBe(".uml:fa887bea143f42006c9b425fd3d210be4293851b.png");
        fs.rmSync(result);
    });
})
