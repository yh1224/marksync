package marksync.remote.qiita

data class QiitaItemTag(
    val name: String,
    val versions: List<String> = listOf()
)
