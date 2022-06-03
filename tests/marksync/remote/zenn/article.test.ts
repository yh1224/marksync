import fs from "fs";
import os from "os";
import path from "path";
import {ZennArticle} from "../../../../src/marksync/remote/zenn/article";

describe("ZennArticle", () => {
    const article1 = new ZennArticle({
        slug: "slug",
        url: "url",
        type: "type",
        topics: ["topic1"],
        published: false,
        body: "body\n",
        title: "title"
    });

    test("DocumentId", () => {
        expect(article1.getDocumentId()).toBe("slug");
    })

    test("DocumentUrl", () => {
        expect(article1.getDocumentUrl()).toBe("url");
    })

    test("Digest", async () => {
        expect(await article1.getDigest()).toBe("8a42f156b7e85884fa53f63f03829da8059d8cc6");
    })

    test("DocumentTitle", () => {
        expect(article1.getDocumentTitle()).toBe("title");
    })

    test("DocumentBody", async () => {
        expect(await article1.getDocumentBody()).toBe("body\n");
    })

    test("getDocumentBody_UML", async () => {
        const article = new ZennArticle({
            ...article1,
            body: "あいうえお\n```uml\na -> b\n```\nかきくけこ\n",
        })
        const result = await article.getDocumentBody();

        expect(result).toBe("あいうえお\n![](.uml:9cead15a2c3d06e9f9627b9906b8d23373035287.png)\nかきくけこ\n");
        expect(fs.existsSync(article.files[".uml:9cead15a2c3d06e9f9627b9906b8d23373035287.png"])).toBeTruthy();
    })

    test("saveBody", () => {
        const dirPath = fs.mkdtempSync(path.join(os.tmpdir(), "marksync-test"));
        process.on("exit", () => fs.rmSync(dirPath, {recursive: true}));
        const filePath = path.join(dirPath, "test.md");
        article1.saveBody(filePath);

        expect(fs.readFileSync(filePath).toString()).toBe("# title\n\nbody\n");
    })

    test("isModified", async () => {
        expect(await article1.isModified(new ZennArticle(article1))).toBeFalsy();
        expect(await article1.isModified(new ZennArticle({...article1, type: "types"}))).toBeTruthy();
        expect(await article1.isModified(new ZennArticle({...article1, topics: []}))).toBeTruthy();
        expect(await article1.isModified(new ZennArticle({...article1, topics: ["topic2"]}))).toBeTruthy();
        expect(await article1.isModified(new ZennArticle({...article1, topics: ["topic1", "topic2"]}))).toBeTruthy();
        expect(await article1.isModified(new ZennArticle({...article1, body: "body2"}))).toBeTruthy();
        expect(await article1.isModified(new ZennArticle({...article1, title: "title2"}))).toBeTruthy();
    })
})
