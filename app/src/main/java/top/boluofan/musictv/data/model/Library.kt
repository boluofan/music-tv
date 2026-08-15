package top.boluofan.musictv.data.model

import com.google.gson.annotations.SerializedName

/** 收藏歌手（/api/user/library/artists 数组元素） */
data class LibraryArtistItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("avatar") val avatar: String? = null
)

/** 收藏专辑（/api/user/library/albums 数组元素） */
data class LibraryAlbumItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("img") val img: String? = null,
    @SerializedName("singer") val singer: String? = null
)

data class LibrarySaveResponse(
    @SerializedName("success") val success: Boolean = false
)
