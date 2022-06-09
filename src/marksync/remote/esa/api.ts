import axios from "axios";
import FormData from "form-data";
import * as fs from "fs";
import * as mime from "mime-types";
import * as path from "path";
import {EsaPost, IEsaPost} from "./post";

interface IEsaMember {
    readonly screen_name?: string;
    readonly posts_count?: number;
}

export class EsaMember {
    public readonly screen_name: string;
    public readonly posts_count: number;

    constructor(data: IEsaMember) {
        this.screen_name = data.screen_name!;
        this.posts_count = data.posts_count!;
    }
}

interface IEsaMembersResponse {
    readonly members?: IEsaMember[];
}

class EsaMembersResponse {
    public readonly members: EsaMember[];

    constructor(data: IEsaMembersResponse) {
        this.members = data.members!.map(it => new EsaMember(it));
    }
}

interface IEsaPostsResponse {
    readonly posts?: IEsaPost[];
}

class EsaPostsResponse {
    public readonly posts: EsaPost[];

    constructor(data: IEsaPostsResponse) {
        this.posts = data.posts!.map(it => new EsaPost(it));
    }
}

interface IAttachment {
    readonly endpoint?: string;
    readonly url?: string;
}

class Attachment {
    public readonly endpoint: string;
    public readonly url: string;

    constructor(data: IAttachment) {
        this.endpoint = data.endpoint!;
        this.url = data.url!;
    }
}

interface IUploadPolicies {
    readonly attachment?: IAttachment,
    readonly form?: { [name: string]: string },
}

class UploadPolicies {
    public readonly attachment: Attachment;
    public readonly form: { [name: string]: string };

    constructor(data: IUploadPolicies) {
        this.attachment = new Attachment(data.attachment!);
        this.form = data.form!;
    }
}

/**
 * Client for esa API v1
 *
 * https://docs.esa.io/posts/102
 */
export class EsaApiClient {
    private endpoint = `https://api.esa.io/v1/teams/${this.teamName}`;

    constructor(
        private readonly teamName: string,
        private readonly username: string,
        private readonly accessToken: string,
    ) {
    }

    private headers = {
        "Authorization": `Bearer ${this.accessToken}`,
    }
    private jsonHeaders = {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${this.accessToken}`,
    }

    /**
     * Get members.
     */
    async getMembers(): Promise<EsaMember[] | null> {
        try {
            const url = `${this.endpoint}/members`;
            process.stderr.write(`# GET ${url}\n`);
            const res = await axios.get(url, {headers: this.jsonHeaders});
            return (new EsaMembersResponse(res.data as IEsaMembersResponse)).members;
        } catch (e) {
            if (!axios.isAxiosError(e)) throw e;
            const response = e.response;
            if (response) {
                process.stderr.write(`ERROR: ${response.status} ${response.statusText}: ${JSON.stringify(response.data)}\n`);
            } else {
                process.stderr.write(`ERROR: ${e}\n`);
            }
            return null;
        }
    }

    /**
     * Get member.
     */
    async getMember(username: string): Promise<EsaMember | null> {
        return (await this.getMembers())?.find(it => it.screen_name == username) || null;
    }

    /**
     * Get posts.
     */
    async getPosts(): Promise<EsaPost[] | null> {
        const member = await this.getMember(this.username);
        if (member === null) {
            return [];
        }
        let result: EsaPost[] = [];
        for (let i = 0; i <= (member.posts_count - 1) / 100; i++) {
            const pageResult = await this.getPostsPage(this.username, i + 1);
            if (pageResult === null) {
                return null;
            }
            result = result.concat(pageResult);
        }
        return result;
    }

    /**
     * Get posts per page.
     */
    async getPostsPage(username: string, page: number): Promise<EsaPost[] | null> {
        try {
            const url = `${this.endpoint}/posts?q=user:${username}&page=${page}&per_page=100`;
            process.stderr.write(`# GET ${url}\n`);
            const res = await axios.get(url, {headers: this.jsonHeaders});
            return (new EsaPostsResponse(res.data as IEsaPostsResponse)).posts;
        } catch (e) {
            if (!axios.isAxiosError(e)) throw e;
            const response = e.response;
            if (response) {
                process.stderr.write(`ERROR: ${response.status} ${response.statusText}: ${JSON.stringify(response.data)}\n`);
            } else {
                process.stderr.write(`ERROR: ${e}\n`);
            }
            return null;
        }
    }

    /**
     * Get post.
     */
    async getPost(number: number): Promise<EsaPost | null> {
        try {
            const url = `${this.endpoint}/posts/${number}`;
            process.stderr.write(`# GET ${url}\n`);
            const res = await axios.get(url, {headers: this.jsonHeaders});
            return new EsaPost(res.data as IEsaPost);
        } catch (e) {
            if (!axios.isAxiosError(e)) throw e;
            const response = e.response;
            if (response) {
                process.stderr.write(`ERROR: ${response.status} ${response.statusText}: ${JSON.stringify(response.data)}\n`);
            } else {
                process.stderr.write(`ERROR: ${e}\n`);
            }
            return null;
        }
    }

    /**
     * Save post.
     */
    async savePost(post: EsaPost, message: string | null): Promise<EsaPost | null> {
        const data = {
            post: {
                body_md: await post.getDocumentBody(),
                category: post.category,
                wip: post.wip,
                tags: post.tags,
                name: post.getDocumentTitle(),
                message: message,
            }
        };

        try {
            let res;
            if (post.number === undefined) {
                const url = `${this.endpoint}/posts`;
                process.stderr.write(`# POST ${url}\n`);
                res = await axios.post(url, data, {headers: this.jsonHeaders});
            } else {
                const url = `${this.endpoint}/posts/${post.number}`;
                process.stderr.write(`# PATCH ${url}\n`);
                res = await axios.patch(url, data, {headers: this.jsonHeaders});
            }
            return new EsaPost(res.data as IEsaPost);
        } catch (e) {
            if (!axios.isAxiosError(e)) throw e;
            const response = e.response;
            if (response) {
                process.stderr.write(`ERROR: ${response.status} ${response.statusText}: ${JSON.stringify(response.data)}\n`);
            } else {
                process.stderr.write(`ERROR: ${e}\n`);
            }
            return null;
        }
    }

    /**
     * Get upload policies.
     */
    private async getUploadPolicies(filePath: string, contentType: string): Promise<UploadPolicies | null> {
        try {
            const url = `${this.endpoint}/attachments/policies`;
            process.stderr.write(`# POST ${url}\n`);
            const data = new FormData();
            data.append("type", contentType);
            data.append("name", path.basename(filePath));
            data.append("size", String(fs.statSync(filePath).size));
            const res = await axios.post(url, data, {headers: this.headers, ...data.getHeaders()});
            return new UploadPolicies(res.data as IUploadPolicies);
        } catch (e) {
            if (!axios.isAxiosError(e)) throw e;
            const response = e.response;
            if (response) {
                process.stderr.write(`ERROR: ${response.status} ${response.statusText}: ${JSON.stringify(response.data)}\n`);
            } else {
                process.stderr.write(`ERROR: ${e}\n`);
            }
            return null;
        }
    }

    /**
     * Upload file.
     *
     * @param filePath File path to upload
     * @return URL for uploaded file
     */
    async uploadFile(filePath: string): Promise<string | null> {
        const contentType = mime.lookup(filePath) || "application/octet-stream";
        const policies = await this.getUploadPolicies(filePath, contentType);
        if (policies === null) {
            return null;
        }
        try {
            const url = policies.attachment.endpoint;
            process.stderr.write(`# POST ${url}\n`);
            const data = new FormData();
            for (const [k, v] of Object.entries(policies.form)) {
                data.append(k, v);
            }
            data.append("file", fs.createReadStream(filePath), {
                filename: path.basename(filePath),
                filepath: filePath,
                knownLength: fs.statSync(filePath).size,
                contentType,
            });
            await axios.post(url, data, {
                headers: {...data.getHeaders(), "Content-Length": data.getLengthSync()},
                maxContentLength: Infinity,
                maxBodyLength: Infinity,
            });
            return policies.attachment.url;
        } catch (e) {
            if (!axios.isAxiosError(e)) throw e;
            const response = e.response;
            if (response) {
                process.stderr.write(`ERROR: ${response.status} ${response.statusText}: ${JSON.stringify(response.data)}\n`);
            } else {
                process.stderr.write(`ERROR: ${e}\n`);
            }
            return null;
        }
    }
}
