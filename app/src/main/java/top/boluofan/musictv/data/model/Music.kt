package top.boluofan.musictv.data.model

import com.google.gson.annotations.SerializedName

data class MusicInfo(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("singer") val singer: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("interval") val interval: String? = null,
    @SerializedName("img") val img: String? = null,
    @SerializedName("albumId") val albumId: String? = null,
    @SerializedName("albumName") val albumName: String? = null,
    @SerializedName(value = "songmid", alternate = ["musicId"]) val songmid: String? = null,
    @SerializedName("hash") val hash: String? = null,
    @SerializedName("copyrightId") val copyrightId: String? = null,
    @SerializedName("types") val types: List<QualityInfo>? = null,
    @SerializedName("meta") val meta: MusicMeta? = null
) {
    val picUrl: String?
        get() = img ?: meta?.picUrl

    val songId: String
        get() {
            if (!songmid.isNullOrEmpty()) return songmid
            meta?.songId?.let {
                return it.toString().toBigDecimalOrNull()?.toPlainString() ?: it.toString()
            }
            return id.orEmpty()
        }

    val qualityTypes: List<QualityInfo>?
        get() = types ?: meta?.qualitys
}

data class MusicMeta(
    @SerializedName("songId") val songId: Any? = null,
    @SerializedName("picUrl") val picUrl: String? = null,
    @SerializedName("albumName") val albumName: String? = null,
    @SerializedName("qualitys") val qualitys: List<QualityInfo>? = null
)

data class QualityInfo(
    @SerializedName("type") val type: String? = null,
    @SerializedName("size") val size: String? = null
)

data class MusicUrlResponse(
    @SerializedName("url") val url: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("sourceName") val sourceName: String? = null,
    @SerializedName("attempts") val attempts: List<Map<String, Any?>>? = null,
    @SerializedName("requestedSource") val requestedSource: String? = null,
    @SerializedName("downloadSource") val downloadSource: String? = null
) {
    val isValid: Boolean get() = !url.isNullOrEmpty()
}

data class LyricInfo(
    @SerializedName("lyric") val lyric: String? = null,
    @SerializedName("tlyric") val tlyric: String? = null,
    @SerializedName("rlyric") val rlyric: String? = null,
    @SerializedName("lxlyric") val lxlyric: String? = null
) {
    val hasLyric: Boolean get() = !lyric.isNullOrEmpty()
    val hasTlyric: Boolean get() = !tlyric.isNullOrEmpty()
    val hasLxlyric: Boolean get() = !lxlyric.isNullOrEmpty()
}
