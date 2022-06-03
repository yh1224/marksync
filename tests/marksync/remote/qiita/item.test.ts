import fs from "fs";
import os from "os";
import path from "path";
import {QiitaItem} from "../../../../src/marksync/remote/qiita/item";
import {QiitaItemTag} from "../../../../src/marksync/remote/qiita/tag";

describe("QiitaItem", () => {
    const item1 = new QiitaItem({
        id: "id",
        url: "url",
        tags: [new QiitaItemTag({name: "tag1"})],
        private: false,
        body: "body\n",
        title: "title"
    });

    test("DocumentId", () => {
        expect(item1.getDocumentId()).toBe("id");
    })

    test("DocumentUrl", () => {
        expect(item1.getDocumentUrl()).toBe("url");
    })

    test("Digest", async () => {
        expect(await item1.getDigest()).toBe("73edb66f05c1715d8e1a90471377470ac48c963a");
    })

    test("DocumentTitle", () => {
        expect(item1.getDocumentTitle()).toBe("title");
    })

    test("DocumentBody", async () => {
        expect(await item1.getDocumentBody()).toBe("body\n");
    })

    test("getDocumentBody_UML", async () => {
        const item = new QiitaItem({
            ...item1,
            body: "あいうえお\n\```uml\na -> b\n```\nかきくけこ\n",
        })
        const result = await item.getDocumentBody();

        expect(result).toBe("あいうえお\n![](.uml:9cead15a2c3d06e9f9627b9906b8d23373035287.png)\nかきくけこ\n");
        expect(fs.existsSync(item.files[".uml:9cead15a2c3d06e9f9627b9906b8d23373035287.png"])).toBeTruthy();
    })

    test("saveBody", () => {
        const dirPath = fs.mkdtempSync(path.join(os.tmpdir(), "marksync-test"));
        process.on("exit", () => fs.rmSync(dirPath, {recursive: true}));
        const filePath = path.join(dirPath, "test.md");
        item1.saveBody(filePath);

        expect(fs.readFileSync(filePath).toString()).toBe("# title\n\nbody\n");
    })

    test("isModified", async () => {
        expect(await item1.isModified(new QiitaItem(item1))).toBeFalsy();
        expect(await item1.isModified(new QiitaItem({...item1, tags: []}))).toBeTruthy();
        expect(await item1.isModified(new QiitaItem({...item1, tags: [{name: "tag2"}]}))).toBeTruthy();
        expect(await item1.isModified(new QiitaItem({...item1, tags: [{name: "tag1"}, {name: "tag2"}]}))).toBeTruthy();
        expect(await item1.isModified(new QiitaItem({...item1, body: "body2"}))).toBeTruthy();
        expect(await item1.isModified(new QiitaItem({...item1, title: "title2"}))).toBeTruthy();
    })
})
