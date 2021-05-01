package marksync.uploader

import org.apache.commons.codec.binary.Hex
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

data class FileInfo(
    val filename: String,
    val digest: String?,
    val url: String?
) {
    constructor(filename: String, file: File, url: String?) :
            this(filename, sha1(file), url)

    /**
     * Check if this file is not modified.
     */
    fun isIdenticalTo(file: File): Boolean = digest == sha1(file)

    companion object {
        /**
         * Calculate file digest.
         */
        fun sha1(file: File): String {
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update(Files.readAllBytes(file.toPath()))
            return Hex.encodeHexString(digest.digest())
        }
    }
}
