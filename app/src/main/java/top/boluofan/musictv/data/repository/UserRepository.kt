package top.boluofan.musictv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** 收藏/歌单写入成功后发出，供首页等页面自动刷新（跨 Activity 共享同一实例） */
    private val _listChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val listChanges: SharedFlow<Unit> = _listChanges.asSharedFlow()

    suspend fun getUserList(): ListData = withContext(Dispatchers.IO) {
        ApiClient.getUserApi().getUserList()
    }

    suspend fun saveUserList(list: ListData) {
        ApiClient.getUserApi().updateUserList(list)
    }

    /**
     * 加歌到我的歌单，返回「是否真的写入成功」。
     *
     * lxserver 的 listMusicAdd 按 item.id 去重，缺 id 的 musicInfos 会被静默丢弃却仍返回 200，
     * 因此提交前补齐 id，并在写入后回读 /api/user/list 校验，避免 UI 乐观更新造成假收藏。
     */
    suspend fun addToUserList(listId: String, songs: List<MusicInfo>, location: String = "bottom"): Boolean =
        withContext(Dispatchers.IO) {
            val normalized = songs.map { song ->
                if (song.id.isNullOrBlank()) song.copy(id = song.listId) else song
            }
            val wanted = normalized.mapNotNull { it.id?.takeIf { v -> v.isNotBlank() } }.toSet()
            if (wanted.isEmpty()) return@withContext false

            runCatching {
                ApiClient.getMusicApi().addToUserList(
                    mapOf(
                        "listId" to listId,
                        "musicInfos" to normalized,
                        "location" to location
                    )
                )
            }.getOrElse { return@withContext false }

            val ok = runCatching { listContains(listId, wanted) }.getOrDefault(false)
            if (ok) _listChanges.tryEmit(Unit)
            ok
        }

    /**
     * 从我的歌单移除歌曲，返回「是否真的删除成功」。
     *
     * 服务端 listMusicRemove 同样按 item.id 匹配，这里同时带上已有 id、source_songId、
     * songmid 与 songId 四种候选值，兼容老数据与在线旧后端（Set.delete 多传无害）。
     */
    suspend fun removeFromUserList(listId: String, songs: List<MusicInfo>): Boolean =
        withContext(Dispatchers.IO) {
            val candidates = mutableListOf<String>()
            for (song in songs) {
                song.id?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                candidates.add(song.listId)
                song.songmid?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                if (song.songId.isNotBlank()) candidates.add(song.songId)
            }
            val ids = candidates.filter { it.isNotBlank() }.distinct()
            if (ids.isEmpty()) return@withContext false

            runCatching {
                ApiClient.getMusicApi().removeFromUserList(
                    mapOf("listId" to listId, "songIds" to ids)
                )
            }.getOrElse { return@withContext false }

            val ok = runCatching { !listContains(listId, ids.toSet()) }.getOrDefault(false)
            if (ok) _listChanges.tryEmit(Unit)
            ok
        }

    /** 回读指定歌单，判断其中是否仍有任一候选 id 对应的歌曲 */
    private suspend fun listContains(listId: String, ids: Set<String>): Boolean {
        val data = ApiClient.getUserApi().getUserList()
        val songs: List<MusicInfo> = when (listId) {
            "love" -> data.loveList.orEmpty()
            "default" -> data.defaultList.orEmpty()
            else -> data.userList.orEmpty().firstOrNull { it.id == listId }?.songs.orEmpty()
        }
        return songs.any { song ->
            listOfNotNull(song.id, song.songmid, song.listId, song.songId).any { it in ids }
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
