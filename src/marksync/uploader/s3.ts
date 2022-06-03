import * as fs from "fs";
import * as mime from "mime-types";
import * as path from "path";
import * as uuid from "uuid";
import {PutObjectCommand, S3Client, S3ServiceException} from "@aws-sdk/client-s3";
import {PutObjectCommandInput} from "@aws-sdk/client-s3/dist-types/commands/PutObjectCommand";
import {Uploader} from "./uploader";

export class S3Uploader implements Uploader {
    private readonly s3Client: S3Client;

    constructor(
        private readonly bucketName: string,
        private readonly prefix: string | undefined,
        private readonly baseUrl: string,
    ) {
        this.s3Client = new S3Client({});
    }

    async upload(filePath: string): Promise<string | null> {
        const lastModified = new Date(fs.statSync(filePath).mtimeMs);
        const datePrefix = lastModified.toISOString().substring(0, 10).replace(/-/g, "/");
        const ext = path.extname(filePath);
        const name = uuid.v4();
        const uploadFilePath = `${datePrefix}/${name}${ext}`;
        let objectKey;
        if (this.prefix === undefined) {
            objectKey = uploadFilePath;
        } else {
            objectKey = `${this.prefix}/${uploadFilePath}`;
        }
        const contentType = mime.lookup(filePath);

        try {
            const input: PutObjectCommandInput = {
                Bucket: this.bucketName,
                Key: objectKey,
                Body: fs.readFileSync(filePath),
                ACL: "public-read",
            }
            if (contentType) {
                input.ContentType = contentType;
            }
            await this.s3Client.send(new PutObjectCommand(input));
            return `${this.baseUrl}/${objectKey}`;
        } catch (e) {
            if (!(e instanceof S3ServiceException)) {
                throw e;
            }
            process.stderr.write(`ERROR: ${e.message}\n`);
            return null;
        }
    }
}
