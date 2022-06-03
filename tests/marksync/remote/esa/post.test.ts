import fs from "fs";
import os from "os";
import path from "path";
import {EsaPost} from "../../../../src/marksync/remote/esa/post";

describe("EsaPost", () => {
    const post1 = new EsaPost({
        number: 123,
        url: "url",
        category: "category",
        tags: ["tag1"],
        wip: false,
        body_md: "body\n",
        name: "name",
    });

    test("getDocumentId", () => {
        expect(post1.getDocumentId()).toBe("123");
    });

    test("getDocumentUrl", () => {
        expect(post1.getDocumentUrl()).toBe("url");
    });

    test("getDigest", async () => {
        expect(await post1.getDigest()).toBe("0bcbb85112d686e0e8ab6af5024eae6bd1c1bd57");
    });

    test("getDocumentTitle", () => {
        expect(post1.getDocumentTitle()).toBe("name");
    });

    test("getDocumentBody", async () => {
        const post = new EsaPost({
            ...post1,
            body_md: "あいうえお\n```plantuml\na -> b\n```\nかきくけこ\n",
        });
        const result = await post.getDocumentBody();
        expect(result).toBe("あいうえお\n```uml\na -> b\n```\nかきくけこ\n");
    });

    test("saveBody", async () => {
        const dirPath = fs.mkdtempSync(path.join(os.tmpdir(), "marksync-test"));
        process.on("exit", () => fs.rmSync(dirPath, {recursive: true}));
        const filePath = path.join(dirPath, "test.md");
        post1.saveBody(filePath);

        expect(fs.readFileSync(filePath).toString()).toBe("# name\n\nbody\n");
    });

    test("isModified", async () => {
        expect(await post1.isModified(new EsaPost(post1))).toBeFalsy();
        expect(await post1.isModified(new EsaPost({...post1, category: "category2"}))).toBeTruthy();
        expect(await post1.isModified(new EsaPost({...post1, tags: []}))).toBeTruthy();
        expect(await post1.isModified(new EsaPost({...post1, tags: ["tag2"]}))).toBeTruthy();
        expect(await post1.isModified(new EsaPost({...post1, tags: ["tag1", "tag2"]}))).toBeTruthy();
        expect(await post1.isModified(new EsaPost({...post1, body_md: "body2"}))).toBeTruthy();
        expect(await post1.isModified(new EsaPost({...post1, name: "name2"}))).toBeTruthy();
    });
})
