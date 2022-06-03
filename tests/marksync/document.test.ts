import {LocalDocument} from "../../src/marksync/document";

describe("LocalDocument", () => {
    test("of", () => {
        const result = LocalDocument.of("tests/resources/doc/test1");

        expect(result.title).toBe("テスト1");
        expect(result.body).toBe("本文1\n本文2\n本文3\n\n![](image1.png)\n");
        expect(Object.keys(result.getFiles())).toStrictEqual(["image1.png"])
    });
})
