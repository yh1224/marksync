import fs from "fs";
import os from "os";
import path from "path";
import {EsaApiClient} from "../../../../src/marksync/remote/esa/api";
import {EsaPost} from "../../../../src/marksync/remote/esa/post";
import {EsaService} from "../../../../src/marksync/remote/esa/service";
import {LocalDocument} from "../../../../src/marksync/document";

jest.mock("../../../../src/marksync/remote/esa/api");
const esaApiClientMock = new EsaApiClient("", "", "") as jest.Mocked<EsaApiClient>;

describe("EsaService", () => {
    const post1 = new EsaPost({
        number: 1,
        category: "category1",
        tags: ["tag1"],
        wip: false,
        body_md: "body1",
        name: "title1"
    })
    const post2 = new EsaPost({
        number: 2,
        category: "category2",
        tags: ["tag2"],
        wip: false,
        body_md: "body2",
        name: "title2"
    })

    test("getDocuments", async () => {
        esaApiClientMock.getPosts.mockImplementation(async () => {
            return [post1, post2];
        });
        const service = new EsaService("esa", esaApiClientMock);
        const result = await service.getDocuments();

        expect(esaApiClientMock.getPosts.mock.calls.length).toBe(1);
        expect(esaApiClientMock.getPosts.mock.calls[0]).toEqual([]);
        expect(result!["1"]).toBe(post1);
        expect(result!["2"]).toBe(post2);
    })

    test("getDocument", async () => {
        esaApiClientMock.getPost.mockImplementation(async () => {
            return post1;
        });
        const service = new EsaService("esa", esaApiClientMock);
        const result = await service.getDocument("123");

        expect(esaApiClientMock.getPost.mock.calls.length).toBe(1);
        expect(esaApiClientMock.getPost.mock.calls[0]).toEqual([123]);
        expect(result).toBe(post1);
    })

    test("toServiceDocument", () => {
        const service = new EsaService("esa", esaApiClientMock);
        const dirPath = "tests/resources/doc/test1";
        const doc = LocalDocument.of(dirPath);
        const result = service.toServiceDocument(doc, dirPath);
        const [resultDoc, resultDigest] = result!;

        expect(resultDigest).toBeNull();
        expect(resultDoc.name).toBe("テスト1");
    })

    test("createMeta", () => {
        const service = new EsaService("esa", esaApiClientMock);
        const dirPath = fs.mkdtempSync(path.join(os.tmpdir(), "marksync-test"));
        process.on("exit", () => fs.rmSync(dirPath, {recursive: true}));
        service.createMeta(dirPath);

        expect(fs.readFileSync(path.join(dirPath, "marksync.esa.yml")).toString()).toBe(
            "tags: []\n" +
            "wip: true\n" +
            "files: []\n"
        );
    })

    test("saveMeta", async () => {
        const service = new EsaService("esa", esaApiClientMock);
        const dirPath = fs.mkdtempSync(path.join(os.tmpdir(), "marksync-test"));
        process.on("exit", () => fs.rmSync(dirPath, {recursive: true}));
        await service.saveMeta(post1, dirPath);

        expect(fs.readFileSync(path.join(dirPath, "marksync.esa.yml")).toString()).toBe(
            "tags:\n" +
            "  - \"tag1\"\n" +
            "wip: false\n" +
            "files: []\n" +
            "number: 1\n" +
            "digest: \"59a685e19c0d23484be9df90d6b970beb2648f7c\"\n" +
            "category: \"category1\"\n"
        );
    })

    test("update", async () => {
        esaApiClientMock.savePost.mockImplementation(async () => {
            return new EsaPost({...post1, number: 456});
        });
        const service = new EsaService("esa", esaApiClientMock);
        const result = await service.update(post1, "message");

        expect(esaApiClientMock.savePost.mock.calls.length).toBe(1);
        expect(esaApiClientMock.savePost.mock.calls[0]).toEqual([post1, "message"]);
        expect(result?.getDocumentId()).toBe("456");
    })
})
