package top.boluofan.musictv.data.model

data class LyricWord(
    val start: Long,
    val end: Long,
    val text: String
)

data class LyricLine(
    val time: Long,
    val text: String,
    val words: List<LyricWord>? = null,
    val translation: String? = null,
    val romaji: String? = null
) {
    val hasWords: Boolean get() = !words.isNullOrEmpty()
}
