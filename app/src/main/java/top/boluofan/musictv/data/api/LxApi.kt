package top.boluofan.musictv.data.api

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import top.boluofan.musictv.data.model.AlbumSongsResponse
import top.boluofan.musictv.data.model.ArtistAlbumsResponse
import top.boluofan.musictv.data.model.ArtistDetail
import top.boluofan.musictv.data.model.AuthVerifyResponse
import top.boluofan.musictv.data.model.HotSearchResponse
import top.boluofan.musictv.data.model.LeaderboardBoardsResponse
import top.boluofan.musictv.data.model.LeaderboardListResponse
import top.boluofan.musictv.data.model.ListData
import top.boluofan.musictv.data.model.LoginResponse
import top.boluofan.musictv.data.model.LyricInfo
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.model.MusicUrlResponse
import top.boluofan.musictv.data.model.SimpleResponse
import top.boluofan.musictv.data.model.SongListDetailResponse
import top.boluofan.musictv.data.model.SongListPageResponse
import top.boluofan.musictv.data.model.SongListTagsResponse
import top.boluofan.musictv.data.model.AlbumItem
import top.boluofan.musictv.data.model.Playlist
import top.boluofan.musictv.data.model.SearchArtistItem
import top.boluofan.musictv.data.model.LibraryArtistItem
import top.boluofan.musictv.data.model.LibraryAlbumItem
import top.boluofan.musictv.data.model.LibrarySaveResponse

/** 用户/认证接口，baseUrl = {server}/api/user/，x-user-name / x-user-token 由 AuthInterceptor 附加 */
interface LxUserApi {
    @POST("login")
    suspend fun login(@Body body: Map<String, String>): LoginResponse

    @GET("auth/verify")
    suspend fun verifyAuth(): AuthVerifyResponse

    @POST("logout")
    suspend fun logout(): SimpleResponse

    @GET("list")
    suspend fun getUserList(): ListData

    @POST("list")
    suspend fun updateUserList(@Body list: ListData): SimpleResponse

    @GET("library/artists")
    suspend fun getLibraryArtists(): List<LibraryArtistItem>

    @POST("library/artists")
    suspend fun saveLibraryArtists(@Body list: List<LibraryArtistItem>): LibrarySaveResponse

    @GET("library/albums")
    suspend fun getLibraryAlbums(): List<LibraryAlbumItem>

    @POST("library/albums")
    suspend fun saveLibraryAlbums(@Body list: List<LibraryAlbumItem>): LibrarySaveResponse
}

/** 音乐接口，baseUrl = {server}/api/music/ */
interface LxMusicApi {
    @GET("search")
    suspend fun search(
        @Query("name") name: String,
        @Query("source") source: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("type") type: String = "song",
        @Query("pages") pages: Int? = null
    ): List<MusicInfo>

    @GET("search")
    suspend fun searchSingers(
        @Query("name") name: String,
        @Query("source") source: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("type") type: String = "singer"
    ): List<SearchArtistItem>

    @GET("search")
    suspend fun searchAlbums(
        @Query("name") name: String,
        @Query("source") source: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("type") type: String = "album"
    ): List<AlbumItem>

    @GET("search")
    suspend fun searchPlaylists(
        @Query("name") name: String,
        @Query("source") source: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("type") type: String = "playlist"
    ): List<Playlist>

    @POST("url")
    suspend fun getMusicUrl(@Body body: @JvmSuppressWildcards Map<String, Any>): MusicUrlResponse

    @GET("lyric")
    suspend fun getLyric(
        @Query("source") source: String,
        @Query("songmid") songmid: String,
        @Query("refresh") refresh: Boolean? = null
    ): LyricInfo

    @GET("hotSearch")
    suspend fun getHotSearch(@Query("source") source: String): HotSearchResponse

    @GET("tipSearch")
    suspend fun getTipSearch(
        @Query("name") name: String,
        @Query("source") source: String
    ): List<String>

    @GET("songList/tags")
    suspend fun getSongListTags(@Query("source") source: String): SongListTagsResponse

    @GET("songList/list")
    suspend fun getSongListList(
        @Query("source") source: String,
        @Query("tagId") tagId: String?,
        @Query("sortId") sortId: String?,
        @Query("page") page: Int
    ): SongListPageResponse

    @GET("songList/detail")
    suspend fun getSongListDetail(
        @Query("source") source: String,
        @Query("id") id: String,
        @Query("page") page: Int
    ): SongListDetailResponse

    @GET("songList/search")
    suspend fun searchSongList(
        @Query("source") source: String,
        @Query("text") text: String,
        @Query("page") page: Int
    ): SongListPageResponse

    @GET("leaderboard/boards")
    suspend fun getLeaderboardBoards(@Query("source") source: String): LeaderboardBoardsResponse

    @GET("leaderboard/list")
    suspend fun getLeaderboardList(
        @Query("source") source: String,
        @Query("bangid") bangId: String,
        @Query("page") page: Int
    ): LeaderboardListResponse

    @GET("artistDetail")
    suspend fun getArtistDetail(
        @Query("source") source: String,
        @Query("id") id: String
    ): ArtistDetail

    @GET("artistAlbums")
    suspend fun getArtistAlbums(
        @Query("source") source: String,
        @Query("id") id: String,
        @Query("page") page: Int
    ): ArtistAlbumsResponse

    @GET("artistSongs")
    suspend fun getArtistSongs(
        @Query("source") source: String,
        @Query("id") id: String,
        @Query("order") order: String = "hot"
    ): List<MusicInfo>

    @GET("albumSongs")
    suspend fun getAlbumSongs(
        @Query("source") source: String,
        @Query("id") id: String
    ): AlbumSongsResponse

    /** 加歌到我的歌单，body {listId, musicInfos, location}，成功返回纯文本 */
    @POST("user/list/add")
    suspend fun addToUserList(@Body body: @JvmSuppressWildcards Map<String, Any>): ResponseBody

    /** 从我的歌单批量删除，body {listId, songIds}，成功返回纯文本 */
    @POST("user/list/remove")
    suspend fun removeFromUserList(@Body body: @JvmSuppressWildcards Map<String, Any>): ResponseBody
}

/** 歌单管理接口，baseUrl = {server}/api/data/，x-frontend-auth 由调用方传入 */
interface LxDataApi {
    @POST("rename-playlist")
    suspend fun renamePlaylist(@Body body: @JvmSuppressWildcards Map<String, Any>): ResponseBody

    @POST("delete-playlist")
    suspend fun deletePlaylist(@Body body: @JvmSuppressWildcards Map<String, Any>): ResponseBody

    @POST("delete-song")
    suspend fun deleteSong(@Body body: @JvmSuppressWildcards Map<String, Any>): ResponseBody

    @POST("batch-delete-songs")
    suspend fun batchDeleteSongs(@Body body: @JvmSuppressWildcards Map<String, Any>): ResponseBody
}
