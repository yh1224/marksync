package marksync.uploader

import java.io.File

interface Uploader {
    /**
     * Upload file.
     *
     * @param file file to upload
     * @return URL
     */
    fun upload(file: File): String?
}
