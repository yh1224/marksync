package marksync.services.qiita

import marksync.uploader.FileInfo

data class QiitaItemMeta(
    val id: String? = null,
    val url: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val digest: String? = null,
    val tags: List<QiitaItemTag> = listOf(),
    val `private`: Boolean = true,
    val files: ArrayList<FileInfo> = arrayListOf()
)
