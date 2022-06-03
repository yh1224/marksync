import axios from "axios";
import {EsaApiClient, EsaMember} from "../../../../src/marksync/remote/esa/api";
import {EsaPost} from "../../../../src/marksync/remote/esa/post";

jest.mock("axios");
const axiosMock = axios as jest.Mocked<typeof axios>;

describe("EsaApiClient", () => {
    const apiGetMockDefaultImplementation = async (url: string) => {
        if (url === "https://api.esa.io/v1/teams/team/members") {
            // response users
            return {
                data: {
                    members: [
                        {screen_name: "user1", posts_count: 200},
                        {screen_name: "user2", posts_count: 2},
                    ],
                },
            };
        } else if (url === "https://api.esa.io/v1/teams/team/posts?q=user:user1&page=1&per_page=100") {
            // response posts for user1, page 1
            return {
                data: {
                    posts: [
                        {
                            number: 1,
                            url: "https://docs.esa.io/posts/1",
                            created_at: "2015-05-09T11:54:50+09:00",
                            updated_at: "2015-05-09T11:54:51+09:00",
                            category: "category1",
                            tags: ["tag1", "tag2"],
                            wip: true,
                            body_md: "body1",
                            name: "name1",
                        },
                    ],
                }
            };
        } else if (url === "https://api.esa.io/v1/teams/team/posts?q=user:user1&page=2&per_page=100") {
            // response posts for user1, page 2
            return {
                data: {
                    posts: [
                        {
                            number: 2,
                            url: "https://docs.esa.io/posts/2",
                            created_at: "2015-05-10T11:54:50+09:00",
                            updated_at: "2015-05-10T11:54:51+09:00",
                            category: "category2",
                            tags: ["tag3", "tag4"],
                            wip: false,
                            body_md: "body2",
                            name: "name2",
                        },
                    ],
                }
            };
        }
    };

    test("getMembers", async () => {
        axiosMock.get.mockImplementation(apiGetMockDefaultImplementation);
        const apiClient = new EsaApiClient("team", "user1", "accessToken");
        const result = await apiClient.getMembers();

        expect(axios.get).toHaveBeenCalledTimes(1);
        expect(axios.get).toHaveBeenCalledWith("https://api.esa.io/v1/teams/team/members",
            {headers: {Authorization: "Bearer accessToken", "Content-Type": "application/json"}});
        expect(result).toStrictEqual([
            new EsaMember({screen_name: "user1", posts_count: 200}),
            new EsaMember({screen_name: "user2", posts_count: 2}),
        ])
    })

    test("getMember", async () => {
        axiosMock.get.mockImplementation(apiGetMockDefaultImplementation);
        const apiClient = new EsaApiClient("team", "user1", "accessToken");
        const result = await apiClient.getMember("user1");

        expect(result).toStrictEqual(new EsaMember({screen_name: "user1", posts_count: 200}));
    })

    test("getPosts", async () => {
        const apiClient = new EsaApiClient("team", "user1", "accessToken");
        const result = await apiClient.getPosts();

        expect(result).toStrictEqual([
            new EsaPost({
                number: 1,
                url: "https://docs.esa.io/posts/1",
                created_at: "2015-05-09T11:54:50+09:00",
                updated_at: "2015-05-09T11:54:51+09:00",
                category: "category1",
                tags: ["tag1", "tag2"],
                wip: true,
                body_md: "body1",
                name: "name1",
            }),
            new EsaPost({
                number: 2,
                url: "https://docs.esa.io/posts/2",
                created_at: "2015-05-10T11:54:50+09:00",
                updated_at: "2015-05-10T11:54:51+09:00",
                category: "category2",
                tags: ["tag3", "tag4"],
                wip: false,
                body_md: "body2",
                name: "name2",
            }),
        ]);
    })

    test("getPost", async () => {
        axiosMock.get.mockImplementation(async (url) => {
            if (url === "https://api.esa.io/v1/teams/team/posts/11") {
                return {
                    data: {
                        number: 11,
                        url: "https://docs.esa.io/posts/11",
                        created_at: "2015-05-09T11:54:50+09:00",
                        updated_at: "2015-05-09T11:54:51+09:00",
                        category: "category11",
                        tags: ["tag11", "tag12"],
                        wip: true,
                        body_md: "body11",
                        name: "name11"
                    }
                };
            } else {
                return await apiGetMockDefaultImplementation(url);
            }
        });
        const apiClient = new EsaApiClient("team", "user1", "accessToken");
        const result = await apiClient.getPost(11);

        expect(result).toStrictEqual(
            new EsaPost({
                number: 11,
                url: "https://docs.esa.io/posts/11",
                created_at: "2015-05-09T11:54:50+09:00",
                updated_at: "2015-05-09T11:54:51+09:00",
                category: "category11",
                tags: ["tag11", "tag12"],
                wip: true,
                body_md: "body11",
                name: "name11",
            }),
        )
    })

    test("savePost_new", async () => {
        axiosMock.post.mockImplementation(async (url) => {
            if (url === "https://api.esa.io/v1/teams/team/posts") {
                return {
                    data: {
                        number: 123,
                        url: "https://docs.esa.io/posts/123",
                        created_at: "2015-05-09T11:54:50+09:00",
                        updated_at: "2015-05-09T11:54:51+09:00",
                        category: "category123",
                        tags: ["tag123"],
                        wip: true,
                        body_md: "body123",
                        name: "name123"
                    }
                };
            }
        });
        const apiClient = new EsaApiClient("team", "user1", "accessToken");
        const result = await apiClient.savePost(
            new EsaPost({
                category: "category123",
                tags: ["tag123"],
                wip: true,
                body_md: "body123",
                name: "name123",
            }),
            "message"
        )

        expect(axiosMock.post.mock.calls.length).toBe(1);
        expect(axiosMock.post.mock.calls[0]).toEqual([
            "https://api.esa.io/v1/teams/team/posts",
            {
                post: {
                    body_md: "body123",
                    category: "category123",
                    wip: true,
                    tags: ["tag123"],
                    name: "name123",
                    message: "message",
                },
            },
            {
                headers: {Authorization: "Bearer accessToken", "Content-Type": "application/json"},
            },
        ]);
        expect(result?.getDocumentId()).toBe("123");
    })

    test("savePost_modify", async () => {
        axiosMock.patch.mockImplementation(async (url) => {
            if (url === "https://api.esa.io/v1/teams/team/posts/123") {
                return {
                    data: {
                        number: 123,
                        url: "https://docs.esa.io/posts/123",
                        created_at: "2015-05-09T11:54:50+09:00",
                        updated_at: "2015-05-09T11:54:51+09:00",
                        category: "category123",
                        tags: ["tag123"],
                        wip: true,
                        body_md: "body123",
                        name: "name123"
                    }
                };
            } else {
                return await apiGetMockDefaultImplementation(url);
            }
        });

        const apiClient = new EsaApiClient("team", "user1", "accessToken");
        const result = await apiClient.savePost(
            new EsaPost({
                number: 123,
                category: "category123",
                tags: ["tag123"],
                wip: true,
                body_md: "body123",
                name: "name123",
            }),
            "message"
        );

        expect(axiosMock.patch.mock.calls.length).toBe(1);
        expect(axiosMock.patch.mock.calls[0]).toEqual([
            "https://api.esa.io/v1/teams/team/posts/123",
            {
                post: {
                    body_md: "body123",
                    category: "category123",
                    wip: true,
                    tags: ["tag123"],
                    name: "name123",
                    message: "message"
                }
            },
            {
                headers: {Authorization: "Bearer accessToken", "Content-Type": "application/json"},
            },
        ]);
        expect(result?.getDocumentId()).toBe("123");
    })

    test("uploadFile", () => {
        // TODO
    })
})
