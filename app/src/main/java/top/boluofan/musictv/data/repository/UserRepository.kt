package top.boluofan.musictv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.boluofan.musictv.data.api.ApiClient
import top.boluofan.musictv.data.model.LibraryAlbumItem
import top.boluofan.musictv.data.model.LibraryArtistItem
import top.boluofan.musictv.data.model.ListData
import top.boluofan.musictv.data.model.LyricLine
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.domain.LyricParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor() {

    suspend fun getUserList(): ListData = withContext(Dispatchers.IO) {
        ApiClient.getUserApi().getUserList()
    }

    suspend fun saveUserList(list: ListData) {
        ApiClient.getUserApi().updateUserList(list)
    }

    /** 加歌到我的歌单（lxserver 纯文本响应，仅校验 HTTP 状态） */
    suspend fun addToUserList(listId: String, songs: List<MusicInfo>, location: String = "bottom") {
        withContext(Dispatchers.IO) {
            ApiClient.getMusicApi().addToUserList(
                mapOf(
                    "listId" to listId,
                    "musicInfos" to songs,
                    "location" to location
                )
            )
        }
    }

    /** 从我的歌单批量删除 */
    suspend fun removeFromUserList(listId: String, songIds: List<String>) {
        withContext(Dispatchers.IO) {
            ApiClient.getMusicApi().removeFromUserList(
                mapOf("listId" to listId, "songIds" to songIds)
            )
        }
    }

    // ---- 歌曲歌词 ----

    suspend fun getSongLyric(song: MusicInfo): Result<List<LyricLine>> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = ApiClient.getMusicApi().getLyric(
                source = song.source.orEmpty(),
                songmid = song.songId
            )
            LyricParser.parsePayload(
                lyric = resp.lyric,
                tlyric = resp.tlyric,
                rlyric = resp.rlyric,
                lxlyric = resp.lxlyric
            )
        }
    }

    /** refresh=1 让服务端跳过自动缓存（空/scraped/cached）重跑歌词插件搜索；权威歌词（file/embedded/manual）不被覆盖 */
    suspend fun getSongLyricRefreshed(song: MusicInfo): Result<List<LyricLine>> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = ApiClient.getMusicApi().getLyric(
                source = song.source.orEmpty(),
                songmid = song.songId,
                refresh = true
            )
            LyricParser.parsePayload(
                lyric = resp.lyric,
                tlyric = resp.tlyric,
                rlyric = resp.rlyric,
                lxlyric = resp.lxlyric
            )
        }
    }

    /** 我的收藏（loveList）歌曲 */
    suspend fun getLoveSongs(): List<MusicInfo> = withContext(Dispatchers.IO) {
        ApiClient.getUserApi().getUserList().loveList.orEmpty()
    }

    // ---- 收藏歌手/专辑（library/artists、library/albums 全量覆盖） ----

    suspend fun getLibraryArtists(): List<LibraryArtistItem> = withContext(Dispatchers.IO) {
        ApiClient.getUserApi().getLibraryArtists()
    }

    suspend fun saveLibraryArtists(list: List<LibraryArtistItem>) {
        withContext(Dispatchers.IO) {
            ApiClient.getUserApi().saveLibraryArtists(list)
        }
    }

    suspend fun getLibraryAlbums(): List<LibraryAlbumItem> = withContext(Dispatchers.IO) {
        ApiClient.getUserApi().getLibraryAlbums()
    }

    suspend fun saveLibraryAlbums(list: List<LibraryAlbumItem>) {
        withContext(Dispatchers.IO) {
            ApiClient.getUserApi().saveLibraryAlbums(list)
        }
    }

    /** 切换歌手收藏，返回切换后是否已收藏 */
    suspend fun toggleArtist(item: LibraryArtistItem): Boolean {
        val list = getLibraryArtists()
        val exists = list.any { it.id == item.id && it.source == item.source }
        return if (exists) {
            saveLibraryArtists(list.filterNot { it.id == item.id && it.source == item.source })
            false
        } else {
            saveLibraryArtists(list + item)
            true
        }
    }

    /** 切换专辑收藏，返回切换后是否已收藏 */
    suspend fun toggleAlbum(item: LibraryAlbumItem): Boolean {
        val list = getLibraryAlbums()
        val exists = list.any { it.id == item.id && it.source == item.source }
        return if (exists) {
            saveLibraryAlbums(list.filterNot { it.id == item.id && it.source == item.source })
            false
        } else {
            saveLibraryAlbums(list + item)
            true
        }
    }

    // ---- 我的歌单重命名/删除（GET + POST /api/user/list 全量快照） ----

    /** 重命名自定义歌单 */
    suspend fun renamePlaylist(listId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val data = ApiClient.getUserApi().getUserList()
        val lists = data.userList.orEmpty()
        val target = lists.find { it.id == listId } ?: return@withContext false
        if (target.name == newName) return@withContext true
        ApiClient.getUserApi().updateUserList(
            data.copy(userList = lists.map { if (it.id == listId) it.copy(name = newName) else it })
        )
        true
    }

    /** 删除自定义歌单 */
    suspend fun deletePlaylist(listId: String): Boolean = withContext(Dispatchers.IO) {
        val data = ApiClient.getUserApi().getUserList()
        val lists = data.userList.orEmpty()
        if (lists.none { it.id == listId }) return@withContext false
        ApiClient.getUserApi().updateUserList(
            data.copy(userList = lists.filterNot { it.id == listId })
        )
        true
    }
}
