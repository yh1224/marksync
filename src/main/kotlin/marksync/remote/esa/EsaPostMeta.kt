package marksync.remote.esa

import marksync.uploader.FileInfo

data class EsaPostMeta(
    val number: Int? = null,
    val url: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val digest: String? = null,
    val category: String = "",
    val tags: List<String> = listOf(),
    val wip: Boolean = true,
    val files: ArrayList<FileInfo> = arrayListOf()
)
