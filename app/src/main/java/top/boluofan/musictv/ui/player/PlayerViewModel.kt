package top.boluofan.musictv.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.model.LyricLine
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.repository.UserRepository
import top.boluofan.musictv.data.storage.PreferencesDataStore
import top.boluofan.musictv.domain.LyricParser
import top.boluofan.musictv.domain.PlayMode
import top.boluofan.musictv.domain.PlayerController
import javax.inject.Inject

data class PlayerUiState(
    val currentSong: MusicInfo? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playMode: PlayMode = PlayMode.ORDER,
    val lyrics: List<LyricLine> = emptyList(),
    val currentLyricIndex: Int = -1,
    val showControls: Boolean = true,
    val queue: List<MusicInfo> = emptyList(),
    val currentIndex: Int = -1,
    val showQueueDrawer: Boolean = false,
    val isBuffering: Boolean = false,
    val isFavorite: Boolean = false,
    val isLyricRefreshing: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val userRepository: UserRepository,
    private val dataStore: PreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var lyricSongId: String? = null

    // 我的收藏（loveList）歌曲 key 集合，key = source:songId
    private var favoriteKeys: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            refreshFavoriteKeys()
        }
        viewModelScope.launch {
            playerController.state.collect { s ->
                _uiState.update {
                    it.copy(
                        currentSong = s.currentSong,
                        isPlaying = s.isPlaying,
                        isBuffering = s.isBuffering,
                        duration = s.duration,
                        playMode = s.playMode,
                        queue = s.queue,
                        currentIndex = s.currentIndex
                    )
                }
                val songId = s.currentSong?.songId
                if (songId != null && songId != lyricSongId) {
                    lyricSongId = songId
                    loadLyrics(s.currentSong!!)
                }
                _uiState.update {
                    it.copy(isFavorite = s.currentSong?.let { song -> song.key() in favoriteKeys } == true)
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.isPlaying) {
                    updatePosition(playerController.currentPosition())
                    val duration = playerController.duration()
                    if (duration > 0 && duration != _uiState.value.duration) {
                        _uiState.update { it.copy(duration = duration) }
                    }
                }
                // 逐字歌词需要更高刷新率才能平滑高亮
                val hasWords = _uiState.value.lyrics.any { it.hasWords }
                delay(if (hasWords) 60L else 500L)
            }
        }
    }

    fun playSong(song: MusicInfo, queue: List<MusicInfo> = listOf(song), index: Int = 0) {
        playerController.play(queue, index)
    }

    fun togglePlay() = playerController.togglePlay()

    fun seekTo(position: Long) {
        playerController.seekTo(position)
        updatePosition(position)
    }

    fun seekBy(deltaMs: Long) {
        val duration = playerController.duration()
        val target = (playerController.currentPosition() + deltaMs)
            .coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        seekTo(target)
    }

    fun nextTrack() = playerController.next()

    fun previousTrack() = playerController.previous()

    fun playAt(index: Int) = playerController.playAt(index)

    fun cyclePlayMode() = playerController.cyclePlayMode()

    fun withPlayer(action: (androidx.media3.common.Player) -> Unit) = playerController.withPlayer(action)

    fun toggleFavorite() {
        val song = _uiState.value.currentSong ?: return
        viewModelScope.launch {
            val key = song.key()
            val isFav = key in favoriteKeys
            if (isFav) {
                userRepository.removeFromUserList("love", listOf(song.songId))
                favoriteKeys = favoriteKeys - key
            } else {
                userRepository.addToUserList("love", listOf(song), location = "bottom")
                favoriteKeys = favoriteKeys + key
            }
            _uiState.update { it.copy(isFavorite = !isFav) }
        }
    }

    fun toggleQueueDrawer() {
        _uiState.update { it.copy(showQueueDrawer = !it.showQueueDrawer) }
    }

    fun closeQueueDrawer() {
        _uiState.update { it.copy(showQueueDrawer = false) }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun hideControls() {
        _uiState.update { it.copy(showControls = false) }
    }

    fun showControls() {
        _uiState.update { it.copy(showControls = true) }
    }

    fun updatePosition(position: Long) {
        val lyrics = _uiState.value.lyrics
        val index = lyrics.indexOfLast { it.time <= position }
        _uiState.update {
            it.copy(currentPosition = position, currentLyricIndex = index)
        }
    }

    /** 手动重新拉取歌词：refresh=1 让服务端跳过自动缓存（空/scraped/cached）重跑歌词插件搜索；权威歌词（file/embedded/manual）不被覆盖 */
    fun refreshLyrics() {
        val song = _uiState.value.currentSong ?: return
        if (_uiState.value.isLyricRefreshing) return
        _uiState.update { it.copy(isLyricRefreshing = true) }
        viewModelScope.launch {
            val song = _uiState.value.currentSong
            if (song != null) {
                userRepository.getSongLyricRefreshed(song).onSuccess { parsed ->
                    _uiState.update { it.copy(lyrics = parsed) }
                }
            }
            _uiState.update { it.copy(isLyricRefreshing = false) }
        }
    }

    private suspend fun refreshFavoriteKeys() {
        runCatching {
            userRepository.getLoveSongs().map { it.key() }.toSet()
        }.onSuccess {
            favoriteKeys = it
            _uiState.update {
                it.copy(isFavorite = it.currentSong?.let { song -> song.key() in favoriteKeys } == true)
            }
        }
    }

    private fun loadLyrics(song: MusicInfo) {
        _uiState.update { it.copy(lyrics = emptyList(), currentLyricIndex = -1, isLyricRefreshing = false) }
        viewModelScope.launch {
            userRepository.getSongLyric(song).onSuccess { parsed ->
                _uiState.update { it.copy(lyrics = parsed) }
            }
        }
    }
}

private fun MusicInfo.key(): String = "${source}:${songId}"
