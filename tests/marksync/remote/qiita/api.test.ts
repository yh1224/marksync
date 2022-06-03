import axios from "axios";
import {QiitaApiClient, QiitaUser} from "../../../../src/marksync/remote/qiita/api";
import {QiitaItem} from "../../../../src/marksync/remote/qiita/item";
import {QiitaItemTag} from "../../../../src/marksync/remote/qiita/tag";

jest.mock("axios");
const axiosMock = axios as jest.Mocked<typeof axios>;

describe("QiitaApiClient", () => {
    const apiGetMockDefaultImplementation = async (url: string) => {
        if (url === "https://qiita.com/api/v2/users/user1") {
            // response user
            return {
                data: {
                    id: "user1",
                    items_count: 200,
                },
            }
        } else if (url === "https://qiita.com/api/v2/items?query=user:user1&page=1&per_page=100") {
            // response posts for user1, page 1
            return {
                data: [
                    {
                        id: "1",
                        url: "https://qiita.com/Qiita/items/1",
                        created_at: "2000-01-01T00:00:00+00:00",
                        updated_at: "2000-01-01T00:00:00+00:00",
                        tags: [{"name": "tag1", "versions": []}, {"name": "tag2", "versions": []}],
                        private: false,
                        body: "body1",
                        title: "title1",
                    },
                ],
            }
        } else if (url === "https://qiita.com/api/v2/items?query=user:user1&page=2&per_page=100") {
            // response posts for user1, page 2
            return {
                data: [
                    {
                        id: "2",
                        url: "https://qiita.com/Qiita/items/2",
                        created_at: "2000-01-02T00:00:00+00:00",
                        updated_at: "2000-01-02T00:00:00+00:00",
                        tags: [{"name": "tag3", "versions": []}, {"name": "tag4", "versions": []}],
                        private: true,
                        body: "body2",
                        title: "title2",
                    },
                ],
            }
        }
    }

    test("User", async () => {
        axiosMock.get.mockImplementation(apiGetMockDefaultImplementation);
        const apiClient = new QiitaApiClient("user1", "accessToken");
        const result = await apiClient.getUser("user1");

        expect(result).toStrictEqual(new QiitaUser({id: "user1", items_count: 200}));
    })

    test("Items", async () => {
        axiosMock.get.mockImplementation(apiGetMockDefaultImplementation);
        const apiClient = new QiitaApiClient("user1", "accessToken");
        const result = await apiClient.getItems();

        expect(result).toStrictEqual([
            new QiitaItem({
                id: "1",
                url: "https://qiita.com/Qiita/items/1",
                created_at: "2000-01-01T00:00:00+00:00",
                updated_at: "2000-01-01T00:00:00+00:00",
                tags: [new QiitaItemTag({name: "tag1"}), new QiitaItemTag({name: "tag2"})],
                private: false,
                body: "body1",
                title: "title1",
            }),
            new QiitaItem({
                id: "2",
                url: "https://qiita.com/Qiita/items/2",
                created_at: "2000-01-02T00:00:00+00:00",
                updated_at: "2000-01-02T00:00:00+00:00",
                tags: [new QiitaItemTag({name: "tag3"}), new QiitaItemTag({name: "tag4"})],
                private: true,
                body: "body2",
                title: "title2",
            }),
        ]);
    })

    test("Item", async () => {
        axiosMock.get.mockImplementation(async (url) => {
            if (url === "https://qiita.com/api/v2/items/11") {
                return {
                    data: {
                        id: "11",
                        url: "https://qiita.com/Qiita/items/11",
                        created_at: "2000-01-11T00:00:00+00:00",
                        updated_at: "2000-01-11T00:00:00+00:00",
                        tags: [{"name": "tag11", "versions": []}, {"name": "tag12", "versions": []}],
                        private: false,
                        body: "body11",
                        title: "title11"
                    }
                };
            } else {
                return await apiGetMockDefaultImplementation(url);
            }
        });
        const apiClient = new QiitaApiClient("user1", "accessToken");
        const result = await apiClient.getItem("11");

        expect(result).toStrictEqual(
            new QiitaItem({
                id: "11",
                url: "https://qiita.com/Qiita/items/11",
                created_at: "2000-01-11T00:00:00+00:00",
                updated_at: "2000-01-11T00:00:00+00:00",
                tags: [new QiitaItemTag({name: "tag11"}), new QiitaItemTag({name: "tag12"})],
                private: false,
                body: "body11",
                title: "title11",
            })
        )
    })

    test("saveItem_new", async () => {
        axiosMock.post.mockImplementation(async (url) => {
            if (url === "https://qiita.com/api/v2/items") {
                return {
                    data: {
                        id: "123",
                        url: "https://qiita.com/Qiita/items/123",
                        created_at: "2000-01-12T00:00:00+00:00",
                        updated_at: "2000-01-12T00:00:00+00:00",
                        tags: [{"name": "tag123", "versions": []}],
                        private: false,
                        body: "body123",
                        title: "title123"
                    }
                };
            }
        });
        const apiClient = new QiitaApiClient("user1", "accessToken");
        const result = await apiClient.saveItem(
            new QiitaItem({
                tags: [new QiitaItemTag({name: "tag123"})],
                private: false,
                body: "body123",
                title: "title123",
            })
        );

        expect(axiosMock.post.mock.calls.length).toBe(1);
        expect(axiosMock.post.mock.calls[0]).toEqual([
            "https://qiita.com/api/v2/items",
            {
                body: "body123",
                private: false,
                tags: [new QiitaItemTag({"name": "tag123", "versions": []})],
                title: "title123",
            },
            {
                headers: {Authorization: "Bearer accessToken", "Content-Type": "application/json"},
            },
        ]);
        expect(result?.getDocumentId()).toBe("123")
    })

    test("saveItem_modify", async () => {
        axiosMock.patch.mockImplementation(async (url) => {
            if (url === "https://qiita.com/api/v2/items/456") {
                return {
                    data: {
                        id: "456",
                        url: "https://qiita.com/Qiita/items/456",
                        created_at: "2000-01-12T00:00:00+00:00",
                        updated_at: "2000-01-12T00:00:00+00:00",
                        tags: [{"name": "tag456", "versions": []}],
                        private: false,
                        body: "body456",
                        title: "title456"
                    }
                };
            }
        });
        const apiClient = new QiitaApiClient("user1", "accessToken");
        const result = await apiClient.saveItem(
            new QiitaItem({
                id: "456",
                tags: [new QiitaItemTag({name: "tag456"})],
                private: false,
                body: "body456",
                title: "title456",
            })
        );

        expect(axiosMock.patch.mock.calls.length).toBe(1);
        expect(axiosMock.patch.mock.calls[0]).toEqual([
            "https://qiita.com/api/v2/items/456",
            {
                body: "body456",
                private: false,
                tags: [new QiitaItemTag({"name": "tag456", "versions": []})],
                title: "title456"
            },
            {
                headers: {Authorization: "Bearer accessToken", "Content-Type": "application/json"},
            }
        ]);
        expect(result?.getDocumentId()).toBe("456");
    })
})
