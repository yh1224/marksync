import axios from "axios";
import {IQiitaItem, QiitaItem} from "./item";

interface IQiitaUser {
    readonly id?: string;
    readonly items_count?: number;
}

export class QiitaUser {
    public readonly id: string;
    public readonly items_count: number;

    constructor(data: IQiitaUser) {
        this.id = data.id!;
        this.items_count = data.items_count!;
    }
}

/**
 * Client for Qiita API v2
 *
 * https://qiita.com/api/v2/docs
 */
export class QiitaApiClient {
    private endpoint = "https://qiita.com/api/v2";

    constructor(
        private readonly username: string,
        private readonly accessToken: string
    ) {
    }

    private headers = {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${this.accessToken}`,
    }

    /**
     * Get user.
     */
    async getUser(username: string): Promise<QiitaUser | null> {
        try {
            const url = `${this.endpoint}/users/${username}`;
            process.stderr.write(`# GET ${url}\n`);
            const res = await axios.get(url);
            return new QiitaUser(res.data as IQiitaUser);
        } catch (e) {
            if (!axios.isAxiosError(e)) throw e;
            const response = e.response!;
            process.stderr.write(`ERROR: ${response.status} ${response.statusText}: ${JSON.stringify(response.data)}\n`);
            return null;
        }
    }

    /**
     * Get items.
     */
    async getItems(): Promise<QiitaItem[]> {
        const user = await this.getUser(this.username);
        let result: QiitaItem[] = [];
        for (let i = 0; i <= (user?.items_count! - 1) / 100; i++) {
            result = result.concat(await this.getItemsPage(i + 1));
        }
        return result;
    }

    /**
     * Get items per page.
     */
    private async getItemsPage(page: number): Promise<QiitaItem[]> {
        try {
            const url = `${this.endpoint}/items?query=user:${this.username}&page=${page}&per_page=100`;
            process.stderr.write(`# GET ${url}\n`);
            const res = await axios.get(url, {headers: this.headers});
            return (res.data as IQiitaItem[]).map(it => new QiitaItem(it));
        } catch (e) {
            if (!axios.isAxiosError(e)) throw e;
            const response = e.response!;
            process.stderr.write(`ERROR: ${response.status} ${response.statusText}: ${JSON.stringify(response.data)}\n`);
            return [];
        }
    }

    /**
     * Get item.
     */
    async getItem(id: string): Promise<QiitaItem | null> {
        try {
            const url = `${this.endpoint}/items/${id}`;
            process.stderr.write(`# GET ${url}\n`);
            const res = await axios.get(url, {headers: this.headers});
            return new QiitaItem(res.data as IQiitaItem);
        } catch (e) {
            if (!axios.isAxiosError(e)) throw e;
            const response = e.response!;
            process.stderr.write(`ERROR: ${response.status} ${response.statusText}: ${JSON.stringify(response.data)}\n`);
            return null;
        }
    }

    /**
     * Save item.
     */
    async saveItem(item: QiitaItem): Promise<QiitaItem | null> {
        const data = {
            body: await item.getDocumentBody(),
            private: item.private,
            tags: item.tags,
            title: item.getDocumentTitle(),
        }

        try {
            let res;
            if (item.id === undefined) {
                const url = `${this.endpoint}/items`;
                process.stderr.write(`# POST ${url}\n`);
                res = await axios.post(url, data, {headers: this.headers});
            } else {
                const url = `${this.endpoint}/items/${item.id}`;
                process.stderr.write(`# PATCH ${url}\n`);
                res = await axios.patch(url, data, {headers: this.headers});
            }
            return new QiitaItem(res.data as IQiitaItem);
        } catch (e) {
            if (!axios.isAxiosError(e)) throw e;
            const response = e.response!;
            process.stderr.write(`ERROR: ${response.status} ${response.statusText}: ${JSON.stringify(response.data)}\n`);
            return null;
        }
    }
}
