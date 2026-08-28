package top.boluofan.musictv.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.api.ApiClient
import top.boluofan.musictv.data.config.KaraokeOrderWebServer
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
    val isLyricRefreshing: Boolean = false,
    val showSoundPanel: Boolean = false,
    // 均衡器
    val eqSupported: Boolean = false,
    val eqEnabled: Boolean = false,
    val eqPreset: String = "flat",
    val eqPresetKeys: List<String> = emptyList(),
    val eqPresetNames: List<String> = emptyList(),
    val eqBands: List<Int> = emptyList(),
    val eqBandFrequencies: List<Int> = emptyList(),
    val eqBandLevelMin: Int = -1500,
    val eqBandLevelMax: Int = 1500,
    // 音效模式
    val sfxEnabled: Boolean = false,
    val sfxMode: String = "virtualizer",
    val sfxStrength: Int = 50,
    val sfxModeKeys: List<String> = emptyList(),
    val sfxModeNames: List<String> = emptyList(),
    val sfxModeSupported: List<Boolean> = emptyList(),
    val sfxSupported: Boolean = false,
    val sfxOnA2dp: Boolean = false,
    val sfxActiveMode: String = "off",
    // K 歌人声消除
    val vocalRemovalEnabled: Boolean = false,
    val vocalRemovalSupported: Boolean = false,
    // K 歌模式
    val karaokeModeEnabled: Boolean = false,
    val karaokeList: List<MusicInfo> = emptyList(),
    val karaokeOrderUrl: String? = null,
    val isAccompanimentOn: Boolean = false,
    val showExitKaraokeConfirm: Boolean = false
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
                        currentIndex = s.currentIndex,
                        eqSupported = s.eqSupported,
                        eqEnabled = s.eqEnabled,
                        eqPreset = s.eqPreset,
                        eqPresetKeys = s.eqPresetKeys,
                        eqPresetNames = s.eqPresetNames,
                        eqBands = s.eqBands,
                        eqBandFrequencies = s.eqBandFrequencies,
                        eqBandLevelMin = s.eqBandLevelMin,
                        eqBandLevelMax = s.eqBandLevelMax,
                        sfxEnabled = s.sfxEnabled,
                        sfxMode = s.sfxMode,
                        sfxStrength = s.sfxStrength,
                        sfxModeKeys = s.sfxModeKeys,
                        sfxModeNames = s.sfxModeNames,
                        sfxModeSupported = s.sfxModeSupported,
                        sfxSupported = s.sfxSupported,
                        sfxOnA2dp = s.sfxOnA2dp,
                        sfxActiveMode = s.sfxActiveMode,
                        vocalRemovalEnabled = s.vocalRemovalEnabled,
                        vocalRemovalSupported = s.vocalRemovalSupported,
                        karaokeList = s.karaokeList,
                        karaokeModeEnabled = s.karaokeActive,
                        isAccompanimentOn = s.vocalRemovalEnabled
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

    fun toggleSoundPanel() {
        val opening = !_uiState.value.showSoundPanel
        _uiState.update { it.copy(showSoundPanel = opening) }
        if (opening) {
            playerController.refreshEqInfo()
            playerController.refreshSfxInfo()
        }
    }

    fun closeSoundPanel() {
        _uiState.update { it.copy(showSoundPanel = false) }
    }

    fun setSfxMode(mode: String) = playerController.setSfxMode(mode)

    fun setSfxStrength(strength: Int) = playerController.setSfxStrength(strength)

    fun setEqualizerEnabled(enabled: Boolean) = playerController.setEqualizerEnabled(enabled)

    fun setEqualizerPreset(preset: String) = playerController.setEqualizerPreset(preset)

    fun setEqualizerBand(index: Int, levelDb: Int) = playerController.setEqualizerBand(index, levelDb)

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

    // === K 歌模式（PR2 + PR3 真实实现）===

    private var karaokeOrderServer: KaraokeOrderWebServer? = null

    fun enterKaraokeMode() {
        if (_uiState.value.karaokeModeEnabled) return
        playerController.enterKaraoke()
        startKaraokeOrderServer()
    }

    fun exitKaraokeMode() {
        if (!_uiState.value.karaokeModeEnabled) return
        stopKaraokeOrderServer()
        playerController.exitKaraoke()
        // 退出 K 歌强制切回原唱（关闭人声消除），保证主播放器始终为原唱
        if (playerController.isAccompanimentOn()) {
            playerController.setAccompanimentMode(false)
        }
        // 从 K 歌回到主播放器时默认暂停，由用户主动继续播放
        playerController.pause()
        _uiState.update { it.copy(showExitKaraokeConfirm = false) }
    }

    fun requestExitKaraoke() {
        if (!_uiState.value.karaokeModeEnabled) return
        _uiState.update { it.copy(showExitKaraokeConfirm = true) }
    }

    fun dismissExitKaraokeConfirm() {
        _uiState.update { it.copy(showExitKaraokeConfirm = false) }
    }

    /** 切换原/伴唱：music-tv 端只走人声消除 processor（无双音轨资源） */
    fun toggleAccompaniment() {
        val next = !playerController.isAccompanimentOn()
        playerController.setAccompanimentMode(next)
    }

    fun karaokeAdd(song: MusicInfo) {
        if (!_uiState.value.karaokeModeEnabled) return
        playerController.karaokeAdd(song)
    }

    fun karaokeRemove(index: Int) {
        if (!_uiState.value.karaokeModeEnabled) return
        playerController.karaokeRemove(index)
    }

    fun karaokeMoveTop(index: Int) {
        if (!_uiState.value.karaokeModeEnabled) return
        playerController.karaokeMoveTop(index)
    }

    fun karaokePlayAt(index: Int) {
        if (!_uiState.value.karaokeModeEnabled) return
        playerController.karaokePlayAt(index)
    }

    /**
     * 启动扫码点歌服务：
     * - 默认端口 9089；占用则扫描 9080-9099 找空端口
     * - 搜索走 music-tv 现有 LxMusicApi.search（歌曲类型），错误兜底空列表
     * - 二维码地址写入 karaokeOrderUrl，KaraokeQrCode 立即可见
     */
    private fun startKaraokeOrderServer() {
        if (karaokeOrderServer != null) return
        val ip = top.boluofan.musictv.ui.config.ConfigWebServer.localIpAddress() ?: run {
            Log.w(TAG, "扫码点歌：未获取到局域网 IP，server 启动跳过")
            return
        }
        val ports = (KaraokeOrderWebServer.PORT_SCAN_START..KaraokeOrderWebServer.PORT_SCAN_END).toList()
        for (port in ports) {
            val server = KaraokeOrderWebServer(
                port = port,
                onOrderSearch = { keyword, source -> searchSongsForOrder(keyword, source) },
                onOrderAdd = { song -> karaokeAdd(song) },
                onOrderTop = { index -> karaokeMoveTop(index) },
                onOrderRemove = { index -> karaokeRemove(index) },
                onOrderQueue = { playerController.getKaraokeList() }
            )
            if (runCatching { server.start() }.isSuccess) {
                karaokeOrderServer = server
                val url = "http://$ip:$port/"
                _uiState.update { it.copy(karaokeOrderUrl = url) }
                Log.i(TAG, "扫码点歌已启动：$url")
                return
            }
        }
        Log.e(TAG, "扫码点歌：9080-9099 全部端口被占用，server 启动失败")
    }

    private fun stopKaraokeOrderServer() {
        karaokeOrderServer?.let { runCatching { it.stop() } }
        karaokeOrderServer = null
        _uiState.update { it.copy(karaokeOrderUrl = null) }
    }

    /**
     * 扫码点歌搜索：复用 music-tv 现有 LxMusicApi.search（歌曲类型，limit=20）。
     * 网络异常/未登录等所有错误一律兜底返回空列表（design §6 风险表）。
     * 用 runBlocking 桥接 suspend → 同步回调（NanoHTTPD 工作线程已是后台线程，OK）。
     */
    private fun searchSongsForOrder(keyword: String, source: String): List<MusicInfo> {
        return runCatching {
            runBlocking {
                ApiClient.getMusicApi().search(
                    name = keyword,
                    source = source,
                    page = 1,
                    limit = 20,
                    type = "song"
                )
            }
        }.getOrDefault(emptyList())
    }
}

private fun MusicInfo.key(): String = "${source}:${songId}"

private const val TAG = "PlayerViewModel"
