export interface MarksyncEnv {
    readonly SERVICE?: string;
    readonly SERVICE_NAME?: string;

    readonly UPLOADER?: string;

    readonly AWS_PROFILE?: string;
    readonly AWS_ACCESS_KEY_ID?: string;
    readonly AWS_SECRET_ACCESS_KEY?: string;

    readonly S3_BUCKET_NAME?: string;
    readonly S3_PREFIX?: string;
    readonly S3_BASE_URL?: string;

    // Qiita
    readonly QIITA_USERNAME?: string;
    readonly QIITA_ACCESS_TOKEN?: string;

    // esa.io
    readonly ESA_TEAM?: string;
    readonly ESA_USERNAME?: string;
    readonly ESA_ACCESS_TOKEN?: string;

    // Zenn
    readonly ZENN_USERNAME?: string;
    readonly ZENN_GIT_DIR?: string;
    readonly ZENN_GIT_URL?: string;
    readonly ZENN_GIT_BRANCH?: string;
    readonly ZENN_GIT_USERNAME?: string;
    readonly ZENN_GIT_PASSWORD?: string;
}
