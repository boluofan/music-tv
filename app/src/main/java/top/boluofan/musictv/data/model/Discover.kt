package top.boluofan.musictv.data.model

import com.google.gson.annotations.SerializedName

data class HotSearchResponse(
    @SerializedName("source") val source: String? = null,
    @SerializedName("list") val list: List<String>? = null
)

data class SearchArtistItem(
    @SerializedName(value = "id", alternate = ["singerid", "artistId"]) val id: String? = null,
    @SerializedName(value = "name", alternate = ["singername", "artistName"]) val name: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("pic") val pic: String? = null,
    @SerializedName("img") val img: String? = null,
    @SerializedName("picUrl") val picUrlField: String? = null
) {
    val picUrl: String? get() = picUrlField ?: avatar ?: pic ?: img
}

data class SongListTagsResponse(
    @SerializedName("tags") val tags: List<TagItem>? = null,
    @SerializedName("hotTag") val hotTag: List<HotTagItem>? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("sortList") val sortList: List<SortItem>? = null
)

data class HotTagItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("source") val source: String? = null
)

data class TagItem(
    @SerializedName("name") val name: String? = null,
    @SerializedName("list") val list: List<TagGroup>? = null
)

data class TagGroup(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("parent_id") val parentId: String? = null,
    @SerializedName("parent_name") val parentName: String? = null,
    @SerializedName("source") val source: String? = null
)

data class SortItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null
)

data class SongListPageResponse(
    @SerializedName("list") val list: List<Playlist>? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 30,
    @SerializedName("source") val source: String? = null
)

data class SongListDetailResponse(
    @SerializedName("list") val list: List<MusicInfo>? = null,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 30,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("source") val source: String? = null,
    @SerializedName("info") val info: PlaylistInfo? = null
)

data class LeaderboardBoardsResponse(
    @SerializedName("list") val list: List<BoardItem>? = null,
    @SerializedName("source") val source: String? = null
)

data class LeaderboardListResponse(
    @SerializedName("list") val list: List<MusicInfo>? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 100,
    @SerializedName("source") val source: String? = null
)

data class BoardItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("bangid") val bangid: String? = null
)

data class ArtistDetail(
    @SerializedName("source") val source: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("desc") val desc: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("musicSize") val musicSize: String? = null,
    @SerializedName("albumSize") val albumSize: String? = null
)

data class ArtistAlbumsResponse(
    @SerializedName("source") val source: String? = null,
    @SerializedName("list") val list: List<AlbumItem>? = null,
    @SerializedName("total") val total: Int = 0
)

data class AlbumItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("img") val img: String? = null,
    @SerializedName("picUrl") val picUrlField: String? = null,
    @SerializedName("singer") val singer: String? = null,
    @SerializedName(value = "artistName", alternate = ["artist"]) val artistName: String? = null,
    @SerializedName("publishTime") val publishTime: String? = null,
    @SerializedName("total") val total: String? = null
) {
    val coverUrl: String? get() = picUrlField ?: img
    val singerName: String? get() = artistName ?: singer
}

data class AlbumSongsResponse(
    @SerializedName("list") val list: List<MusicInfo>? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("publishTime") val publishTime: String? = null,
    @SerializedName("source") val source: String? = null
)
