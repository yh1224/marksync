import fs from "fs";
import os from "os";
import path from "path";
import {ZennArticle} from "../../../../src/marksync/remote/zenn/article";
import {ZennRepository} from "../../../../src/marksync/remote/zenn/repository";
import {ZennService} from "../../../../src/marksync/remote/zenn/service";
import {LocalDocument} from "../../../../src/marksync/document";

jest.mock("../../../../src/marksync/remote/zenn/repository");
const zennRepositoryMock = new ZennRepository("", "", "", "", "", "") as jest.Mocked<ZennRepository>;

describe("ZennService", () => {
    const article1 = new ZennArticle({
        slug: "slug1",
        type: "type1",
        topics: ["topic1"],
        published: false,
        body: "body1",
        title: "title1"
    })
    const article2 = new ZennArticle({
        slug: "slug2",
        type: "type2",
        topics: ["topic2"],
        published: false,
        body: "body2",
        title: "title2"
    })

    test("Documents", async () => {
        zennRepositoryMock.getArticles.mockImplementation(async () => {
            return [article1, article2];
        });
        const service = new ZennService("zenn", zennRepositoryMock);
        const result = await service.getDocuments();

        expect(zennRepositoryMock.getArticles.mock.calls.length).toBe(1);
        expect(zennRepositoryMock.getArticles.mock.calls[0]).toEqual([]);
        expect(result!["slug1"]).toBe(article1);
        expect(result!["slug2"]).toBe(article2);
    })

    test("Document", async () => {
        zennRepositoryMock.getArticle.mockImplementation(async () => {
            return article1;
        });
        const service = new ZennService("zenn", zennRepositoryMock);
        const result = await service.getDocument("slug");

        expect(zennRepositoryMock.getArticle.mock.calls.length).toBe(1);
        expect(zennRepositoryMock.getArticle.mock.calls[0]).toEqual(["slug"]);
        expect(result).toBe(article1);
    })

    test("toServiceDocument", () => {
        const service = new ZennService("zenn", zennRepositoryMock);
        const dirPath = "tests/resources/doc/test1";
        const doc = LocalDocument.of(dirPath);
        const result = service.toServiceDocument(doc, dirPath);
        const [resultDoc, resultDigest] = result!;

        expect(resultDigest).toBeNull();
        expect(resultDoc!.title).toBe("テスト1");
    })

    test("createMeta", () => {
        const service = new ZennService("zenn", zennRepositoryMock);
        const dirPath = fs.mkdtempSync(path.join(os.tmpdir(), "marksync-test"));
        process.on("exit", () => fs.rmSync(dirPath, {recursive: true}));
        service.createMeta(dirPath);

        expect(fs.readFileSync(path.join(dirPath, "marksync.zenn.yml")).toString()).toBe(
            "type: \"tech\"\n" +
            "topics: []\n" +
            "published: false\n" +
            "files: []\n"
        )
    })

    test("saveMeta", async () => {
        const service = new ZennService("zenn", zennRepositoryMock);
        const dirPath = fs.mkdtempSync(path.join(os.tmpdir(), "marksync-test"));
        process.on("exit", () => fs.rmSync(dirPath, {recursive: true}));
        await service.saveMeta(article1, dirPath);

        expect(fs.readFileSync(path.join(dirPath, "marksync.zenn.yml")).toString()).toBe(
            "type: \"type1\"\n" +
            "topics:\n" +
            "  - \"topic1\"\n" +
            "published: false\n" +
            "files: []\n" +
            "slug: \"slug1\"\n" +
            "digest: \"c0d3b2d0cbcfed2146f89c4f6549e67383d36ca7\"\n"
        )
    })

    test("update", async () => {
        zennRepositoryMock.saveArticle.mockImplementation(async () => {
            return new ZennArticle({...article1, slug: "slug"});
        });
        const service = new ZennService("zenn", zennRepositoryMock);
        const result = await service.update(article1, "message");

        expect(zennRepositoryMock.saveArticle.mock.calls.length).toBe(1);
        expect(zennRepositoryMock.saveArticle.mock.calls[0]).toEqual([article1, "message"]);
        expect(result?.getDocumentId()).toBe("slug");
    })
})
