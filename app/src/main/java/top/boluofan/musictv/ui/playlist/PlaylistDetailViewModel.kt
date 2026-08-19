package top.boluofan.musictv.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.api.ApiClient
import top.boluofan.musictv.data.model.ListData
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.model.Playlist
import top.boluofan.musictv.data.repository.UserRepository
import javax.inject.Inject

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val songs: List<MusicInfo> = emptyList(),
    val isLoading: Boolean = true,
    val isUserList: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /**
     * 加载歌单详情：
     * - [source] 为 null：我的歌单（default/love/userList），歌曲内嵌在 /api/user/list 响应中
     * - [source] 非 null：歌单广场歌单，走 songList/detail
     */
    fun load(playlistId: String, source: String?) {
        loadJob?.cancel()
        _uiState.value = PlaylistDetailUiState(isLoading = true)
        loadJob = viewModelScope.launch {
            if (source == null) {
                loadFromUserList(playlistId)
            } else {
                loadFromServer(source, playlistId)
            }
        }
    }

    private suspend fun loadFromUserList(playlistId: String) {
        runCatching { userRepository.getUserList() }.fold(
            onSuccess = { list -> resolveFromListData(list, playlistId) },
            onFailure = { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        )
    }

    private fun resolveFromListData(list: ListData, playlistId: String) {
        val (playlist, songs) = when (playlistId) {
            "default" -> Playlist(id = "default", name = "默认列表", songs = list.defaultList) to list.defaultList.orEmpty()
            "love" -> Playlist(id = "love", name = "我的收藏", songs = list.loveList) to list.loveList.orEmpty()
            else -> {
                val p = list.userList.orEmpty().find { it.id == playlistId }
                if (p != null) p to p.songs.orEmpty()
                else null to emptyList()
            }
        }
        if (playlist == null) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "歌单不存在")
        } else {
            _uiState.value = _uiState.value.copy(
                playlist = playlist,
                songs = songs,
                isUserList = playlistId != "default" && playlistId != "love",
                isLoading = false
            )
        }
    }

    private suspend fun loadFromServer(source: String, playlistId: String) {
        runCatching {
            val resp = ApiClient.getMusicApi().getSongListDetail(source, playlistId, page = 1)
            val info = resp.info
            Playlist(
                id = playlistId,
                source = source,
                name = info?.name ?: resp.source,
                img = info?.img,
                desc = info?.desc,
                author = info?.author,
                playCountStr = info?.playCount,
                songs = resp.list
            ) to resp.list.orEmpty()
        }.fold(
            onSuccess = { (playlist, songs) ->
                _uiState.value = _uiState.value.copy(playlist = playlist, songs = songs, isLoading = false)
            },
            onFailure = { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        )
    }

    /** 从我的歌单移除单曲（同步更新本地列表） */
    fun removeSong(songId: String?) {
        if (songId == null) return
        val state = _uiState.value
        val pid = state.playlist?.id ?: return
        viewModelScope.launch {
            runCatching { userRepository.removeFromUserList(pid, listOf(songId)) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        songs = state.songs.filterNot { it.songId == songId }
                    )
                }
        }
    }

    /** 重命名我的歌单 */
    fun renamePlaylist(newName: String, onDone: (Boolean) -> Unit = {}) {
        val state = _uiState.value
        val pid = state.playlist?.id ?: return
        if (newName.isBlank()) return
        viewModelScope.launch {
            runCatching { userRepository.renamePlaylist(pid, newName) }
                .onSuccess {
                    _uiState.value = state.copy(playlist = state.playlist?.copy(name = newName))
                    onDone(true)
                }
                .onFailure { onDone(false) }
        }
    }

    /** 删除我的歌单 */
    fun deletePlaylist(onDone: (Boolean) -> Unit = {}) {
        val state = _uiState.value
        val pid = state.playlist?.id ?: return
        viewModelScope.launch {
            runCatching { userRepository.deletePlaylist(pid) }
                .onSuccess { onDone(true) }
                .onFailure { onDone(false) }
        }
    }

    fun clear() {
        loadJob?.cancel()
        _uiState.value = PlaylistDetailUiState()
    }
}
