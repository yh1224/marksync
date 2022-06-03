export interface Uploader {
    /**
     * Upload file.
     *
     * @param filePath upload file path
     * @return URL
     */
    upload(filePath: string): Promise<string | null>;
}
