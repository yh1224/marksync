import {Uploader} from "../../uploader/uploader";
import {EsaApiClient} from "./api";

export class EsaUploader implements Uploader {
    apiClient: EsaApiClient;

    constructor(apiClient: EsaApiClient) {
        this.apiClient = apiClient;
    }

    async upload(filePath: string): Promise<string | null> {
        return await this.apiClient.uploadFile(filePath);
    }
}
