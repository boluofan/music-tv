package top.boluofan.musictv.data.model

import com.google.gson.annotations.SerializedName

data class Playlist(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("sourceListId") val sourceListId: String? = null,
    @SerializedName("locationUpdateTime") val locationUpdateTime: Long? = null,
    @SerializedName("picUrl") val picUrl: String? = null,
    @SerializedName("img") val img: String? = null,
    @SerializedName("desc") val desc: String? = null,
    @SerializedName("songCount") val songCount: Int? = null,
    @SerializedName("trackCount") val trackCount: Int? = null,
    @SerializedName("total") val total: String? = null,
    @SerializedName("time") val time: String? = null,
    @SerializedName("createTime") val createTime: Long? = null,
    @SerializedName("creator") val creator: String? = null,
    @SerializedName("author") val author: String? = null,
    @SerializedName("playCount") val playCount: Long? = null,
    @SerializedName("play_count") val playCountStr: String? = null,
    @SerializedName("info") val info: PlaylistInfo? = null,
    @SerializedName("list") val songs: List<MusicInfo>? = null
) {
    val isDefault: Boolean get() = id == "default"
    val isLove: Boolean get() = id == "love"

    val coverUrl: String?
        get() = info?.img?.takeIf { it.isNotEmpty() } ?: picUrl?.takeIf { it.isNotEmpty() }
            ?: img?.takeIf { it.isNotEmpty() } ?: songs?.firstOrNull()?.picUrl

    val creatorName: String?
        get() = info?.author?.takeIf { it.isNotEmpty() } ?: creator?.takeIf { it.isNotEmpty() } ?: author

    val descText: String?
        get() = info?.desc?.takeIf { it.isNotEmpty() } ?: desc

    val count: Int
        get() = songCount ?: trackCount ?: total?.toIntOrNull() ?: songs?.size ?: 0

    val formattedPlayCount: String
        get() {
            info?.playCount?.takeIf { it.isNotEmpty() }?.let { return it }
            playCountStr?.takeIf { it.isNotEmpty() }?.let { return it }
            val count = playCount ?: return ""
            return when {
                count >= 100_000_000 -> String.format("%.1f亿", count / 100_000_000.0)
                count >= 10_000 -> String.format("%.1f万", count / 10_000.0)
                else -> count.toString()
            }
        }
}

data class PlaylistInfo(
    @SerializedName("name") val name: String? = null,
    @SerializedName("img") val img: String? = null,
    @SerializedName("author") val author: String? = null,
    @SerializedName("play_count") val playCount: String? = null,
    @SerializedName("desc") val desc: String? = null,
    @SerializedName("time") val time: String? = null
)

data class ListData(
    @SerializedName("defaultList") val defaultList: List<MusicInfo>? = null,
    @SerializedName("loveList") val loveList: List<MusicInfo>? = null,
    @SerializedName("userList") val userList: List<Playlist>? = null,
    @SerializedName("tempList") val tempList: List<MusicInfo>? = null
) {
    val defaultPlaylist: Playlist
        get() = Playlist(id = "default", name = "默认列表", songs = defaultList)
    val lovePlaylist: Playlist
        get() = Playlist(id = "love", name = "我的收藏", songs = loveList)
}
