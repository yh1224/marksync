package marksync.services.zenn

import marksync.uploader.FileInfo

data class ZennDocMeta(
    val slug: String? = null,
    val url: String? = null,
    val digest: String? = null,
    val type: String = "tech",
    val topics: List<String> = listOf(),
    val published: Boolean = false,
    val files: ArrayList<FileInfo> = arrayListOf()
)
