import * as fs from "fs";
import * as os from "os";
import * as path from "path";
import {QiitaApiClient} from "../../../../src/marksync/remote/qiita/api";
import {QiitaItem} from "../../../../src/marksync/remote/qiita/item";
import {QiitaService} from "../../../../src/marksync/remote/qiita/service";
import {QiitaItemTag} from "../../../../src/marksync/remote/qiita/tag";
import {LocalDocument} from "../../../../src/marksync/document";

jest.mock("../../../../src/marksync/remote/qiita/api");
const qiitaApiClientMock = new QiitaApiClient("", "") as jest.Mocked<QiitaApiClient>;

describe("QiitaService", () => {
    const item1 = new QiitaItem({
        id: "id1",
        tags: [new QiitaItemTag({name: "tag1"})],
        private: false,
        body: "body1",
        title: "title1"
    })
    const item2 = new QiitaItem({
        id: "id2",
        tags: [new QiitaItemTag({name: "tag2"})],
        private: false,
        body: "body2",
        title: "title2"
    })

    test("Documents", async () => {
        qiitaApiClientMock.getItems.mockImplementation(async () => {
            return [item1, item2];
        });
        const service = new QiitaService("qiita", qiitaApiClientMock);
        const result = await service.getDocuments();

        expect(qiitaApiClientMock.getItems.mock.calls.length).toBe(1);
        expect(qiitaApiClientMock.getItems.mock.calls[0]).toEqual([]);
        expect(result!["id1"]).toBe(item1);
        expect(result!["id2"]).toBe(item2);
    })

    test("Document", async () => {
        qiitaApiClientMock.getItem.mockImplementation(async () => {
            return item1;
        });
        const service = new QiitaService("qiita", qiitaApiClientMock);
        const result = await service.getDocument("id");

        expect(qiitaApiClientMock.getItem.mock.calls.length).toBe(1);
        expect(qiitaApiClientMock.getItem.mock.calls[0]).toEqual(["id"]);
        expect(result).toBe(item1);
    })

    test("toServiceDocument", () => {
        const service = new QiitaService("qiita", qiitaApiClientMock);
        const dirPath = "tests/resources/doc/test1";
        const doc = LocalDocument.of(dirPath);
        const result = service.toServiceDocument(doc, dirPath);
        const [resultDoc, resultDigest] = result!;

        expect(resultDigest).toBeNull();
        expect(resultDoc.title).toBe("テスト1");
    })

    test("createMeta", () => {
        const service = new QiitaService("qiita", qiitaApiClientMock);
        const dirPath = fs.mkdtempSync(path.join(os.tmpdir(), "marksync-test"));
        process.on("exit", () => fs.rmSync(dirPath, {recursive: true}));
        service.createMeta(dirPath);

        expect(fs.readFileSync(path.join(dirPath, "marksync.qiita.yml")).toString()).toBe(
            "tags: []\n" +
            "private: true\n"
        );
    })

    test("saveMeta", async () => {
        const service = new QiitaService("qiita", qiitaApiClientMock);
        const dirPath = fs.mkdtempSync(path.join(os.tmpdir(), "marksync-test"));
        process.on("exit", () => fs.rmSync(dirPath, {recursive: true}));
        await service.saveMeta(item1, dirPath);

        expect(fs.readFileSync(path.join(dirPath, "marksync.qiita.yml")).toString()).toBe(
            "digest: \"9aa6875ffb8c8aca1d6cd74ebb31ef0e282a19dc\"\n" +
            "files: []\n" +
            "tags:\n" +
            "  - versions: []\n" +
            "    name: \"tag1\"\n" +
            "private: false\n" +
            "id: \"id1\"\n"
        );
    })

    test("update_new", async () => {
        qiitaApiClientMock.saveItem.mockImplementation(async () => {
            return new QiitaItem({...item1, id: "new"});
        });
        const service = new QiitaService("qiita", qiitaApiClientMock);
        const result = await service.update(item1, "message");

        expect(qiitaApiClientMock.saveItem.mock.calls.length).toBe(1);
        expect(qiitaApiClientMock.saveItem.mock.calls[0]).toEqual([item1]);
        expect(result?.getDocumentId()).toBe("new");
    })
})
