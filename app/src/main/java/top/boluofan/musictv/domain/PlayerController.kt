package top.boluofan.musictv.domain

import android.content.ComponentName
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.ln
import kotlin.math.round
import top.boluofan.musictv.MusicService
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.storage.PreferencesDataStore
import top.boluofan.musictv.data.storage.ResumeSnapshot
import top.boluofan.musictv.data.storage.ResumeSnapshotStore
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val queue: List<MusicInfo> = emptyList(),
    val currentIndex: Int = -1,
    val currentSong: MusicInfo? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val duration: Long = 0L,
    val playMode: PlayMode = PlayMode.ORDER,
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemaining: Int = 0,
    val sleepAfterSongs: Int = 0,
    val sleepAfterSongsRemaining: Int = 0,
    // 均衡器（来自 MusicService 的 audiofx.Equalizer，频段增益单位 dB）
    val eqSupported: Boolean = false,
    val eqEnabled: Boolean = false,
    val eqPreset: String = "flat",
    val eqPresetKeys: List<String> = EQ_PRESET_KEYS,
    val eqPresetNames: List<String> = EQ_PRESET_NAMES,
    val eqBands: List<Int> = emptyList(),
    val eqBandFrequencies: List<Int> = emptyList(),
    val eqBandLevelMin: Int = -1500,
    val eqBandLevelMax: Int = 1500,
    // 音效模式（audiofx 效果器，与均衡器独立叠加）：开关 / 模式 key / 强度 0-100
    val sfxEnabled: Boolean = false,
    val sfxMode: String = "virtualizer",
    val sfxStrength: Int = 50,
    val sfxModeKeys: List<String> = SFX_MODE_KEYS,
    val sfxModeNames: List<String> = SFX_MODE_NAMES,
    val sfxModeSupported: List<Boolean> = emptyList(),
    val sfxSupported: Boolean = false,
    val sfxOnA2dp: Boolean = false,
    val sfxActiveMode: String = "off",
    // K 歌人声消除（music-tv 接口只返回单音轨，原/伴唱只走人声消除）
    val vocalRemovalEnabled: Boolean = false,
    val vocalRemovalSupported: Boolean = false,
    // K 歌独立播放列表：与主页播放队列完全隔离，退出 K 歌后还原主页队列
    val karaokeActive: Boolean = false,
    val karaokeList: List<MusicInfo> = emptyList()
)

// 固定均衡器预设（10 段曲线与中文名）：不吃设备系统预设，名称恒定中文、听感跨设备一致
private val EQ_PRESETS = linkedMapOf(
    "flat" to ("平坦" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
    "rock" to ("摇滚" to listOf(5, 4, 2, 0, -1, 1, 3, 4, 5, 4)),
    "pop" to ("流行" to listOf(-1, 2, 4, 5, 4, 2, 0, -1, -1, -1)),
    "jazz" to ("爵士" to listOf(4, 3, 1, 2, -1, -1, 0, 2, 3, 4)),
    "classical" to ("古典" to listOf(5, 4, 3, 2, -1, -1, 0, 3, 4, 5)),
    "bass_boost" to ("低音提升" to listOf(6, 5, 4, 2, 0, 0, 0, 0, 0, 0)),
    "treble_boost" to ("高音增强" to listOf(0, 0, 0, 0, 0, 0, 2, 4, 5, 6)),
    "vocal" to ("人声" to listOf(-2, -1, 0, 3, 5, 5, 3, 1, 0, -2))
)
private val EQ_PRESET_KEYS = EQ_PRESETS.keys.toList() + "custom"
private val EQ_PRESET_NAMES = EQ_PRESETS.values.map { it.first } + "自定义"

// 预设曲线的参考频点（Hz），与 EQ_PRESETS 的增益一一对应
private val EQ_PRESET_FREQS = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

// 音效模式：与均衡器独立叠加；单模式互斥
private val SFX_MODES = linkedMapOf(
    "virtualizer" to "环绕立体声",
    "bass_boost" to "低音增强",
    "loudness" to "响度增强",
    "reverb" to "音乐厅混响"
)
private val SFX_MODE_KEYS = SFX_MODES.keys.toList()
private val SFX_MODE_NAMES = SFX_MODES.values.toList()

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: PreferencesDataStore,
    private val resumeSnapshotStore: ResumeSnapshotStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var eqEnabledCache: Boolean = false
    private var eqBandsCache: List<Int> = emptyList()

    private var sfxEnabledCache: Boolean = false
    private var sfxModeCache: String = "virtualizer"
    private var sfxStrengthCache: Int = 50
    private var vocalRemovalEnabledCache: Boolean = false

    // K 歌独立列表：进入时备份主页队列，退出时还原，期间所有增删/置顶只作用于 karaokeList
    private var mainQueueBackup: List<MusicInfo>? = null
    private var mainIndexBackup: Int = -1
    private var mainPosBackup: Long = 0L

    // 输出设备切换（HDMI/蓝牙/内置喇叭）后音效能力可能变化，主动刷新让 UI 实时感知
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshSfxInfo()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshSfxInfo()
        }
    }

    init {
        // 均衡器配置持久化闭环：UI 只写 DataStore，这里缓存并推送给 MusicService
        scope.launch {
            combine(dataStore.eqEnabled, dataStore.eqBands) { enabled, bands ->
                enabled to parseBands(bands)
            }.collect { (enabled, bands) ->
                eqEnabledCache = enabled
                eqBandsCache = bands
                _state.update { it.copy(eqEnabled = enabled, eqBands = bands) }
                if (controller != null) sendEqApply(enabled, bands)
            }
        }
        scope.launch {
            dataStore.eqPreset.collect { preset ->
                _state.update { it.copy(eqPreset = preset) }
            }
        }
        // 播放模式持久化闭环：重启后恢复上次模式，并在控制器已连接时即时重放
        scope.launch {
            dataStore.playMode.collect { mode ->
                val playMode = runCatching { PlayMode.valueOf(mode) }.getOrDefault(PlayMode.ORDER)
                _state.update { it.copy(playMode = playMode) }
                controller?.let { applyPlayMode(it, playMode) }
            }
        }
        // 音效模式配置持久化闭环，同均衡器
        scope.launch {
            combine(dataStore.sfxEnabled, dataStore.sfxMode, dataStore.sfxStrength) { enabled, mode, strength ->
                Triple(enabled, mode, strength)
            }.collect { (enabled, mode, strength) ->
                // 旧版本可能存有 "off"，模式列表已不含"关闭"，归一化为默认模式
                val effectiveMode = if (mode == "off") "virtualizer" else mode
                sfxEnabledCache = enabled
                sfxModeCache = effectiveMode
                sfxStrengthCache = strength
                _state.update { it.copy(sfxEnabled = enabled, sfxMode = effectiveMode, sfxStrength = strength) }
                if (controller != null) sendSfxApply(enabled, effectiveMode, strength)
            }
        }
        // AudioDeviceCallback 为 API 23+，低版本设备跳过（音效能力按连接时查询为准）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                    .registerAudioDeviceCallback(audioDeviceCallback, null)
            }
        }

        // 进程退出（含设置页重启的 Runtime.exit）前兜底落盘最后进度
        runCatching {
            Runtime.getRuntime().addShutdownHook(Thread {
                runCatching {
                    val snapshot = currentSnapshot()
                    if (snapshot != null) runBlocking { resumeSnapshotStore.save(snapshot) }
                }
            })
        }
        startSnapshotPersistence()
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    // 播放报错自动跳过失效曲目（典型为续播快照中的歌曲已被服务端删除）
    private var autoSkipOnError = false
    private var consecutiveErrors = 0
    private val failedSongIds = mutableSetOf<String>()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startSnapshotPersistence() {
        // 队列/下标变化时防抖保存
        scope.launch {
            _state
                .filter { !it.karaokeActive && it.queue.isNotEmpty() && it.currentIndex in it.queue.indices }
                .map { Pair(it.queue, it.currentIndex) }
                .distinctUntilChanged()
                .debounce(SNAPSHOT_QUEUE_DEBOUNCE_MS)
                .collect { saveCurrentSnapshot() }
        }
        // 播放中周期采样进度
        scope.launch {
            while (true) {
                delay(SNAPSHOT_SAVE_INTERVAL_MS)
                if (_state.value.isPlaying && !_state.value.karaokeActive) {
                    saveCurrentSnapshot()
                }
            }
        }
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previousSong = _state.value.currentSong
            val wasKaraoke = _state.value.karaokeActive
            val activeList = if (wasKaraoke) _state.value.karaokeList else _state.value.queue
            val oldIndex = _state.value.currentIndex
            val song = activeList.firstOrNull { mediaKey(it) == mediaItem?.mediaId }
            val newIndex = controller?.currentMediaItemIndex ?: -1

            countDownSleepAfterSongs(previousSong, song, reason)
            _state.update {
                it.copy(
                    currentSong = song,
                    currentIndex = controller?.currentMediaItemIndex ?: -1,
                    duration = (song?.interval?.toLongOrNull() ?: 0L) * 1000
                )
            }
            // 新歌默认重置人声消除（原唱）；K 歌模式下不重置（用户已在伴唱模式）
            if (song != null && song.songId != previousSong?.songId && !wasKaraoke) {
                consecutiveErrors = 0
                failedSongIds.clear()
                if (_state.value.vocalRemovalEnabled) {
                    setVocalRemovalEnabled(false)
                }
                // 切歌即保存一次快照，兜底短促播放（不足采样间隔）后进程被杀的情况
                if (!_state.value.karaokeActive) saveCurrentSnapshot()
            }

            // K 歌：仅当上一首"自然播放结束"（唱完）才从独立列表中移除，列表始终只保留未唱歌曲；
            // 切歌/跳过不视为唱过，保留在列表中。移除后当前曲前移一位。该操作只影响 karaokeList，
            // 不会改动主播放器 queue。
            if (wasKaraoke && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                && previousSong != null && song != null && song.songId != previousSong.songId
                && oldIndex in _state.value.karaokeList.indices && oldIndex < newIndex
            ) {
                val newList = _state.value.karaokeList.toMutableList().apply { removeAt(oldIndex) }
                _state.update { it.copy(karaokeList = newList, currentIndex = newIndex - 1) }
                withController { c -> if (oldIndex in 0 until c.mediaItemCount) c.removeMediaItem(oldIndex) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                consecutiveErrors = 0
            } else {
                // 暂停时立即落盘：此时进度仍有效，避免暂停后进程被杀丢失位置
                saveCurrentSnapshot()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val duration = controller?.duration ?: 0L
            _state.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    duration = if (duration > 0) duration else it.duration
                )
            }
            // 音频会话在首次播放时才创建，均衡器/音效可能晚于控制器连接就绪，播放就绪后重试一次
            if (playbackState == Player.STATE_READY) {
                if (_state.value.eqBandFrequencies.isEmpty()) retryEqSetup()
                if (_state.value.sfxModeSupported.isEmpty()) retrySfxSetup()
                controller?.let { checkVocalRemovalSupport(it) }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "onPlayerError: ${error.errorCodeName} | ${error.message}", error)
            skipFailedSongIfResuming()
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let { action(it); return }
        val future = controllerFuture ?: MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, MusicService::class.java))
        ).buildAsync().also { controllerFuture = it }

        future.addListener({
            runCatching {
                val c = future.get()
                if (controller == null) {
                    controller = c
                    c.addListener(listener)
                    applyPlayMode(c, _state.value.playMode)
                    // 先恢复已保存配置，再查询能力与频段数据，保证 info 反映应用后的状态
                    sendEqApply(eqEnabledCache, eqBandsCache)
                    checkEqSupport(c)
                    queryEqInfo(c)
                    sendSfxApply(sfxEnabledCache, sfxModeCache, sfxStrengthCache)
                    checkSfxSupport(c)
                    querySfxInfo(c)
                    sendVocalRemovalApply(vocalRemovalEnabledCache)
                    checkVocalRemovalSupport(c)
                }
                action(c)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun play(
        queue: List<MusicInfo>,
        index: Int,
        startPositionMs: Long = 0L
    ) {
        val song = queue.getOrNull(index) ?: return
        consecutiveErrors = 0
        failedSongIds.clear()
        autoSkipOnError = true
        _state.update {
            it.copy(
                queue = queue,
                currentIndex = index,
                currentSong = song,
                duration = (song.interval?.toLongOrNull() ?: 0L) * 1000
            )
        }
        withController { c ->
            c.setMediaItems(queue.map { buildMediaItem(it) }, index, startPositionMs)
            applyPlayMode(c, _state.value.playMode)
            c.prepare()
            c.play()
        }
    }

    /** 续播：恢复快照中的队列与进度并开始播放 */
    fun resumePlayback(snapshot: ResumeSnapshot) {
        val queue = snapshot.queue
        val index = snapshot.index.coerceIn(0, queue.size - 1)
        val position = snapshot.positionMs.coerceAtLeast(0L)
        play(queue, index, startPositionMs = position)
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else c.play()
    }

    fun pause() = withController { it.pause() }

    fun next() = withController { it.seekToNextMediaItem() }

    fun previous() = withController { it.seekToPreviousMediaItem() }

    fun playAt(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    fun seekTo(position: Long) = withController { it.seekTo(position) }

    fun setPlayMode(mode: PlayMode) {
        _state.update { it.copy(playMode = mode) }
        scope.launch { dataStore.setPlayMode(mode.name) }
        withController { applyPlayMode(it, mode) }
    }

    fun cyclePlayMode() {
        setPlayMode(
            when (_state.value.playMode) {
                PlayMode.ORDER -> PlayMode.LOOP
                PlayMode.LOOP -> PlayMode.SINGLE
                PlayMode.SINGLE -> PlayMode.RANDOM
                PlayMode.RANDOM -> PlayMode.ORDER
            }
        )
    }

    fun currentPosition(): Long = controller?.currentPosition ?: 0L

    fun duration(): Long = controller?.duration?.takeIf { it > 0 } ?: _state.value.duration

    fun withPlayer(action: (Player) -> Unit) = withController(action)

    // ===== 均衡器 =====

    fun setEqualizerEnabled(enabled: Boolean, onResult: ((Boolean) -> Unit)? = null) {
        if (!enabled) {
            scope.launch { dataStore.setEqEnabled(false) }
            _state.update { it.copy(eqEnabled = false) }
            onResult?.invoke(true)
            return
        }
        // 开启前校验设备能力，不支持则不写入配置
        scope.launch {
            val supported = checkEqualizerSupport()
            _state.update { it.copy(eqSupported = supported) }
            if (supported) {
                dataStore.setEqEnabled(true)
                _state.update { it.copy(eqEnabled = true) }
            }
            onResult?.invoke(supported)
        }
    }

    // 面板打开时刷新：能力 + 频段数据（数据需音频会话就绪后才有）
    fun refreshEqInfo() = withController { c ->
        checkEqSupport(c)
        queryEqInfo(c)
    }

    private suspend fun checkEqualizerSupport(): Boolean = withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine { cont ->
            withController { c ->
                val future = c.sendCustomCommand(
                    SessionCommand(MusicService.EQ_CHECK, Bundle.EMPTY), Bundle.EMPTY
                )
                future.addListener({
                    val supported = runCatching {
                        val r = future.get()
                        r.resultCode == SessionResult.RESULT_SUCCESS &&
                            r.extras?.getBoolean(MusicService.EXTRA_SUPPORTED, false) == true
                    }.getOrDefault(false)
                    if (cont.isActive) cont.resume(supported)
                }, ContextCompat.getMainExecutor(context))
            }
        }
    } ?: false

    private fun checkEqSupport(c: MediaController) {
        val future = c.sendCustomCommand(
            SessionCommand(MusicService.EQ_CHECK, Bundle.EMPTY), Bundle.EMPTY
        )
        future.addListener({
            runCatching {
                val r = future.get()
                val supported = r.resultCode == SessionResult.RESULT_SUCCESS &&
                    r.extras?.getBoolean(MusicService.EXTRA_SUPPORTED, false) == true
                Log.d(TAG, "eq/check：设备支持均衡器 = $supported")
                _state.update { it.copy(eqSupported = supported) }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setEqualizerPreset(preset: String) {
        if (preset !in EQ_PRESET_KEYS) return
        val bands = resolvePresetBands(preset)
        scope.launch {
            dataStore.setEqPreset(preset)
            dataStore.setEqBands(formatBands(bands))
        }
        _state.update { it.copy(eqPreset = preset, eqBands = bands) }
    }

    fun setEqualizerBand(bandIndex: Int, levelDb: Int) {
        val bands = eqBandsCache.toMutableList()
        if (bandIndex !in bands.indices) return
        val min = _state.value.eqBandLevelMin / 100
        val max = _state.value.eqBandLevelMax / 100
        bands[bandIndex] = levelDb.coerceIn(min, max)
        scope.launch {
            dataStore.setEqBands(formatBands(bands))
            // 手动调频段即视为自定义曲线
            dataStore.setEqPreset("custom")
        }
        _state.update { it.copy(eqBands = bands, eqPreset = "custom") }
    }

    // 预设曲线（10 频点）按对数频率插值到设备实际频段；设备频段未知时按已知段数截取
    private fun resolvePresetBands(preset: String): List<Int> {
        val curve = EQ_PRESETS[preset]?.second ?: return eqBandsCache
        val deviceFreqs = _state.value.eqBandFrequencies
        if (deviceFreqs.isEmpty()) {
            val knownCount = eqBandsCache.size
            return if (knownCount == 0) curve else curve.take(knownCount)
        }
        return deviceFreqs.map { freq -> interpolateGain(freq, EQ_PRESET_FREQS, curve) }
    }

    // 对数频率线性插值（同 songloft-player 的 mpv EQ 映射方式）
    private fun interpolateGain(freq: Int, freqs: List<Int>, gains: List<Int>): Int {
        val logFreq = ln(freq.toDouble())
        val logLow = ln(freqs.first().toDouble())
        val logHigh = ln(freqs.last().toDouble())
        val gain = when {
            logFreq <= logLow -> gains.first().toDouble()
            logFreq >= logHigh -> gains.last().toDouble()
            else -> {
                var result = gains.last().toDouble()
                for (i in 0 until freqs.size - 1) {
                    val lo = ln(freqs[i].toDouble())
                    val hi = ln(freqs[i + 1].toDouble())
                    if (logFreq in lo..hi) {
                        val t = (logFreq - lo) / (hi - lo)
                        result = gains[i] + t * (gains[i + 1] - gains[i])
                        break
                    }
                }
                result
            }
        }
        return round(gain).toInt()
    }

    private fun sendEqApply(enabled: Boolean, bands: List<Int>) {
        val c = controller ?: return
        val args = Bundle().apply {
            putBoolean(MusicService.EXTRA_ENABLED, enabled)
            putIntArray(MusicService.EXTRA_BANDS, bands.toIntArray())
        }
        c.sendCustomCommand(SessionCommand(MusicService.EQ_APPLY, Bundle.EMPTY), args)
    }

    private fun retryEqSetup() {
        val c = controller ?: return
        Log.d(TAG, "播放就绪，重试均衡器（apply + check + query）")
        sendEqApply(eqEnabledCache, eqBandsCache)
        checkEqSupport(c)
        queryEqInfo(c)
    }

    private fun queryEqInfo(c: MediaController) {
        val future = c.sendCustomCommand(SessionCommand(MusicService.EQ_INFO, Bundle.EMPTY), Bundle.EMPTY)
        future.addListener({
            runCatching {
                val result = future.get()
                // 频段数据需音频会话就绪（播放中）才有；失败不影响能力判断（eq/check 负责）
                if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                    Log.w(TAG, "eq/info 响应异常：code=${result.resultCode}")
                    return@addListener
                }
                val extras = result.extras ?: run {
                    Log.w(TAG, "eq/info 无返回数据")
                    return@addListener
                }
                if (!extras.getBoolean(MusicService.EXTRA_SUPPORTED, false)) {
                    Log.w(TAG, "eq/info 绑定失败（音频会话未就绪或设备无 audiofx）")
                    return@addListener
                }
                Log.d(TAG, "eq/info 成功：${extras.getInt(MusicService.EXTRA_BAND_COUNT)} 段")
                _state.update {
                    it.copy(
                        eqSupported = true,
                        eqBandFrequencies = extras.getIntArray(MusicService.EXTRA_CENTER_FREQS)?.toList() ?: emptyList(),
                        eqBandLevelMin = extras.getInt(MusicService.EXTRA_LEVEL_MIN),
                        eqBandLevelMax = extras.getInt(MusicService.EXTRA_LEVEL_MAX),
                        eqEnabled = extras.getBoolean(MusicService.EXTRA_ENABLED, false),
                        eqBands = extras.getIntArray(MusicService.EXTRA_BANDS)?.toList() ?: emptyList()
                    )
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // ===== 音效模式 =====

    fun setSfxEnabled(enabled: Boolean, onResult: ((Boolean) -> Unit)? = null) {
        if (!enabled) {
            scope.launch { dataStore.setSfxEnabled(false) }
            _state.update { it.copy(sfxEnabled = false) }
            onResult?.invoke(true)
            return
        }
        // 开启前校验设备能力，不支持则不写入配置
        scope.launch {
            val supported = checkSfxSupport()
            if (supported) {
                dataStore.setSfxEnabled(true)
                _state.update { it.copy(sfxEnabled = true) }
            }
            onResult?.invoke(supported)
        }
    }

    fun setSfxMode(mode: String) {
        if (mode !in SFX_MODE_KEYS) return
        scope.launch {
            dataStore.setSfxMode(mode)
            // 面板内无"关闭"选项，选择模式即视为开启（总开关在设置页）
            dataStore.setSfxEnabled(true)
        }
        _state.update { it.copy(sfxMode = mode, sfxEnabled = true) }
    }

    fun setSfxStrength(strength: Int) {
        val s = strength.coerceIn(0, 100)
        scope.launch { dataStore.setSfxStrength(s) }
        _state.update { it.copy(sfxStrength = s) }
    }

    // 面板打开时刷新：能力矩阵 + 当前生效状态（数据不依赖音频会话，静态查询）
    fun refreshSfxInfo() = withController { c ->
        checkSfxSupport(c)
        querySfxInfo(c)
    }

    private suspend fun checkSfxSupport(): Boolean = withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine { cont ->
            withController { c ->
                val future = c.sendCustomCommand(
                    SessionCommand(MusicService.SFX_CHECK, Bundle.EMPTY), Bundle.EMPTY
                )
                future.addListener({
                    val supported = runCatching {
                        val r = future.get()
                        r.resultCode == SessionResult.RESULT_SUCCESS &&
                            r.extras?.getBoolean(MusicService.EXTRA_SUPPORTED, false) == true
                    }.getOrDefault(false)
                    if (cont.isActive) cont.resume(supported)
                }, ContextCompat.getMainExecutor(context))
            }
        }
    } ?: false

    private fun checkSfxSupport(c: MediaController) {
        val future = c.sendCustomCommand(
            SessionCommand(MusicService.SFX_CHECK, Bundle.EMPTY), Bundle.EMPTY
        )
        future.addListener({
            runCatching {
                val r = future.get()
                val supported = r.resultCode == SessionResult.RESULT_SUCCESS &&
                    r.extras?.getBoolean(MusicService.EXTRA_SUPPORTED, false) == true
                Log.d(TAG, "sfx/check：设备支持音效 = $supported")
                _state.update { it.copy(sfxSupported = supported) }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun sendSfxApply(enabled: Boolean, mode: String, strength: Int) {
        val c = controller ?: return
        val args = Bundle().apply {
            putBoolean(MusicService.EXTRA_ENABLED, enabled)
            putString(MusicService.EXTRA_MODE, mode)
            putInt(MusicService.EXTRA_STRENGTH, strength)
        }
        c.sendCustomCommand(SessionCommand(MusicService.SFX_APPLY, Bundle.EMPTY), args)
    }

    private fun retrySfxSetup() {
        val c = controller ?: return
        Log.d(TAG, "播放就绪，重试音效（apply + check + query）")
        sendSfxApply(sfxEnabledCache, sfxModeCache, sfxStrengthCache)
        checkSfxSupport(c)
        querySfxInfo(c)
    }

    private fun querySfxInfo(c: MediaController) {
        val future = c.sendCustomCommand(SessionCommand(MusicService.SFX_INFO, Bundle.EMPTY), Bundle.EMPTY)
        future.addListener({
            runCatching {
                val result = future.get()
                if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                    Log.w(TAG, "sfx/info 响应异常：code=${result.resultCode}")
                    return@addListener
                }
                val extras = result.extras ?: return@addListener
                val matrix = extras.getBooleanArray(MusicService.EXTRA_SUPPORTED_MATRIX)
                // 矩阵顺序与 SFX_MODE_KEYS 一一对应（virtualizer/bass_boost/loudness/reverb）
                val modeSupported = buildList {
                    matrix?.forEach { add(it) }
                    while (size < SFX_MODE_KEYS.size) add(false)
                }
                val active = extras.getString(MusicService.EXTRA_ACTIVE_MODE) ?: "off"
                Log.d(TAG, "sfx/info：矩阵=$modeSupported, A2DP=${extras.getBoolean(MusicService.EXTRA_A2DP, false)}")
                _state.update {
                    it.copy(
                        sfxSupported = extras.getBoolean(MusicService.EXTRA_SUPPORTED, false),
                        sfxModeSupported = modeSupported,
                        sfxOnA2dp = extras.getBoolean(MusicService.EXTRA_A2DP, false),
                        sfxActiveMode = active
                    )
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun parseBands(s: String): List<Int> =
        s.split(',').mapNotNull { it.trim().toIntOrNull() }

    private fun formatBands(bands: List<Int>): String = bands.joinToString(",")

    private fun applyPlayMode(c: MediaController, mode: PlayMode) {
        when (mode) {
            PlayMode.ORDER -> {
                c.repeatMode = Player.REPEAT_MODE_OFF
                c.shuffleModeEnabled = false
            }
            PlayMode.LOOP -> {
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.shuffleModeEnabled = false
            }
            PlayMode.SINGLE -> {
                c.repeatMode = Player.REPEAT_MODE_ONE
                c.shuffleModeEnabled = false
            }
            PlayMode.RANDOM -> {
                c.repeatMode = Player.REPEAT_MODE_ALL
                c.shuffleModeEnabled = true
            }
        }
    }

    // 播放地址由 MusicService 的 ResolvingDataSource 按 lxmusic:// 协议延迟解析，
    // 队列中的歌曲只有播放到时才请求 /api/music/url
    private fun buildMediaItem(song: MusicInfo): MediaItem {
        val uri = MusicService.buildResolveUri(song.source, song.songId, song.name)
        return MediaItem.Builder()
            .setMediaId(mediaKey(song))
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.name)
                    .setArtist(song.singer)
                    .setArtworkUri(
                        top.boluofan.musictv.data.api.UrlHelper.resolve(song.picUrl)?.let(Uri::parse)
                    )
                    .build()
            )
            .build()
    }

    private fun mediaKey(song: MusicInfo): String = "${song.source}:${song.songId}"

    private var sleepJob: Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        _state.update {
            it.copy(
                sleepTimerMinutes = minutes,
                sleepTimerRemaining = minutes,
                sleepAfterSongs = 0,
                sleepAfterSongsRemaining = 0
            )
        }
        if (minutes > 0) {
            sleepJob = scope.launch {
                var remaining = minutes
                while (remaining > 0) {
                    delay(60_000L)
                    remaining--
                    _state.update { it.copy(sleepTimerRemaining = remaining) }
                }
                controller?.pause()
                _state.update { it.copy(sleepTimerMinutes = 0, sleepTimerRemaining = 0) }
            }
        }
    }

    fun setSleepAfterSongs(count: Int) {
        sleepJob?.cancel()
        sleepJob = null
        _state.update {
            it.copy(
                sleepTimerMinutes = 0,
                sleepTimerRemaining = 0,
                sleepAfterSongs = count,
                sleepAfterSongsRemaining = count
            )
        }
    }

    private fun countDownSleepAfterSongs(previousSong: MusicInfo?, song: MusicInfo?, reason: Int) {
        val remaining = _state.value.sleepAfterSongsRemaining
        if (remaining <= 0) return
        // 只统计自然播完的歌曲，手动切换/同曲重建不计数
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
        if (previousSong == null || previousSong.songId == song?.songId) return
        val next = remaining - 1
        _state.update { it.copy(sleepAfterSongsRemaining = next) }
        if (next == 0) {
            controller?.pause()
            _state.update { it.copy(sleepAfterSongs = 0) }
        }
    }

    // ===== 人声消除（K 歌原/伴唱）=====
    // music-tv 接口只返回单音轨资源，原/伴唱切换只走人声消除 processor，不做轨道切换

    fun setVocalRemovalEnabled(enabled: Boolean) {
        _state.update { it.copy(vocalRemovalEnabled = enabled) }
        vocalRemovalEnabledCache = enabled
        withController { c ->
            val args = Bundle().apply { putBoolean(MusicService.EXTRA_ENABLED, enabled) }
            c.sendCustomCommand(SessionCommand(MusicService.VOCAL_REMOVE_APPLY, Bundle.EMPTY), args)
        }
    }

    /** 切换原/伴唱：music-tv 端只走人声消除 processor */
    fun setAccompanimentMode(accompaniment: Boolean) {
        setVocalRemovalEnabled(accompaniment)
    }

    /** 当前是否处于"伴唱" */
    fun isAccompanimentOn(): Boolean = _state.value.vocalRemovalEnabled

    // ===== K 歌独立播放列表（与主页队列隔离）=====
    // 进入时备份主页队列并在引擎中载入同一份副本，退出时还原主页队列与进度，
    // 因此期间所有增删/置顶只影响 karaokeList，不会改动主页播放队列。

    /** 进入 K 歌：备份主页队列，载入 K 歌独立列表（初始为当前主页队列副本） */
    fun enterKaraoke() {
        if (_state.value.karaokeActive) return
        val backup = _state.value.queue
        mainQueueBackup = backup
        mainIndexBackup = _state.value.currentIndex
        mainPosBackup = controller?.currentPosition ?: 0L
        // K 歌只关注未唱过的歌曲：从当前播放曲开始截取（丢弃其之前已播放过的曲目），
        // 并保证当前曲位于列表首位（index 0），置顶才能精确落到"下一首"位置。
        val startIndex = _state.value.currentIndex.coerceIn(0, (backup.size - 1).coerceAtLeast(0))
        val list = backup.drop(startIndex)
        _state.update { it.copy(karaokeActive = true, karaokeList = list, currentIndex = 0) }
        loadIntoEngine(list, 0, mainPosBackup)
    }

    /** 退出 K 歌：还原主页队列与播放进度，清空 K 歌列表 */
    fun exitKaraoke() {
        if (!_state.value.karaokeActive) return
        val backup = mainQueueBackup ?: emptyList()
        val idx = mainIndexBackup.coerceIn(0, (backup.size - 1).coerceAtLeast(0))
        val pos = mainPosBackup
        mainQueueBackup = null
        _state.update {
            it.copy(
                karaokeActive = false,
                karaokeList = emptyList(),
                queue = backup,
                currentIndex = idx
            )
        }
        loadIntoEngine(backup, idx, pos)
    }

    /** 仅操作播放引擎媒体项，不触碰主页队列状态（供 K 歌进入/退出时整体替换播放列表） */
    private fun loadIntoEngine(list: List<MusicInfo>, index: Int, posMs: Long) {
        withController { c ->
            val start = index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
            c.setMediaItems(list.map { buildMediaItem(it) }, start, posMs)
            applyPlayMode(c, _state.value.playMode)
            c.prepare()
            c.play()
        }
    }

    /** K 歌点歌：追加到独立列表末尾（与扫码点歌共用） */
    fun karaokeAdd(song: MusicInfo) {
        // NanoHTTPD 工作线程回调，整个函数体切到主线程，避免 MediaController 跨线程异常
        scope.launch {
            if (!_state.value.karaokeActive) return@launch
            if (_state.value.karaokeList.any { it.songId == song.songId }) return@launch
            val newList = _state.value.karaokeList + song
            _state.update { it.copy(karaokeList = newList) }
            withController { c ->
                runCatching { c.addMediaItem(buildMediaItem(song)) }
            }
        }
    }

    /** K 歌置顶：移动到当前演唱曲的下一首（下一个演唱） */
    fun karaokeMoveTop(index: Int) {
        // NanoHTTPD 工作线程回调，整个函数体切到主线程，避免 MediaController 跨线程异常
        scope.launch {
            if (!_state.value.karaokeActive) return@launch
            val list = _state.value.karaokeList
            val cur = _state.value.currentIndex
            if (index !in list.indices || index == cur) return@launch
            val song = list[index]
            val newList = list.toMutableList().apply {
                removeAt(index)
                add((cur + 1).coerceIn(0, size), song)
            }
            _state.update { it.copy(karaokeList = newList, currentIndex = cur) }
            withController { c ->
                val target = (cur + 1).coerceIn(0, c.mediaItemCount)
                runCatching { c.moveMediaItem(index, target) }
            }
        }
    }

    /** K 歌删除：从独立列表移除（不允许删除正在演唱的曲目） */
    fun karaokeRemove(index: Int) {
        // NanoHTTPD 工作线程回调，整个函数体切到主线程，避免 MediaController 跨线程异常
        scope.launch {
            if (!_state.value.karaokeActive) return@launch
            val list = _state.value.karaokeList
            if (index !in list.indices || index == _state.value.currentIndex) return@launch
            val newList = list.toMutableList().apply { removeAt(index) }
            _state.update { it.copy(karaokeList = newList) }
            withController { c ->
                if (index in 0 until c.mediaItemCount) runCatching { c.removeMediaItem(index) }
                _state.update { it.copy(currentIndex = c.currentMediaItemIndex) }
            }
        }
    }

    /** K 歌指定演唱某曲 */
    fun karaokePlayAt(index: Int) {
        if (!_state.value.karaokeActive) return
        withController { c -> if (index in 0 until c.mediaItemCount) c.seekToDefaultPosition(index) }
    }

    /** K 歌独立列表快照（供扫码点歌页读取） */
    fun getKaraokeList(): List<MusicInfo> = _state.value.karaokeList

    private fun sendVocalRemovalApply(enabled: Boolean) {
        val c = controller ?: return
        val args = Bundle().apply { putBoolean(MusicService.EXTRA_ENABLED, enabled) }
        c.sendCustomCommand(SessionCommand(MusicService.VOCAL_REMOVE_APPLY, Bundle.EMPTY), args)
    }

    private fun checkVocalRemovalSupport(c: MediaController) {
        val future = c.sendCustomCommand(
            SessionCommand(MusicService.VOCAL_REMOVE_CHECK, Bundle.EMPTY), Bundle.EMPTY
        )
        future.addListener({
            runCatching {
                val r = future.get()
                val supported = r.resultCode == SessionResult.RESULT_SUCCESS &&
                    r.extras?.getBoolean(MusicService.EXTRA_SUPPORTED, false) == true
                val enabled = r.extras?.getBoolean(MusicService.EXTRA_ENABLED, false) == true
                _state.update {
                    it.copy(
                        vocalRemovalSupported = supported,
                        vocalRemovalEnabled = enabled
                    )
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** 计算当前续播快照（队列、曲下标、进度）；K 歌期间或队列为空返回 null */
    private fun currentSnapshot(): ResumeSnapshot? {
        val s = _state.value
        if (s.karaokeActive) return null
        val index = s.currentIndex
        if (s.queue.isEmpty() || index !in s.queue.indices) return null
        val position = currentPosition()
        return ResumeSnapshot(s.queue, index, position, System.currentTimeMillis())
    }

    /** 异步落盘当前快照（K 歌期间跳过） */
    private fun saveCurrentSnapshot() {
        val snapshot = currentSnapshot() ?: return
        scope.launch { runCatching { resumeSnapshotStore.save(snapshot) } }
    }

    /** 当前曲播放报错时自动跳下一首 */
    private fun skipFailedSongIfResuming() {
        if (!autoSkipOnError) return
        consecutiveErrors++
        val s = _state.value
        val song = s.currentSong
        val songKey = song?.let { mediaKey(it) } ?: return
        if (!failedSongIds.add(songKey)) return
        if (consecutiveErrors > MAX_CONSECUTIVE_SKIP) return
        val c = controller ?: return
        Log.w(TAG, "续播跳过失效曲目: ${song.name}(${song.songId})")
        if (c.currentMediaItemIndex < c.mediaItemCount - 1) {
            c.seekToNextMediaItem()
            c.play()
        } else if (c.hasPreviousMediaItem()) {
            c.seekToPreviousMediaItem()
            c.play()
        }
    }

    companion object {
        private const val TAG = "PlayerController"
        // 续播快照：播放中进度采样间隔 / 队列变化后防抖落盘时长 / 报错自动跳过的连续上限
        private const val SNAPSHOT_SAVE_INTERVAL_MS = 10_000L
        private const val SNAPSHOT_QUEUE_DEBOUNCE_MS = 1_000L
        private const val MAX_CONSECUTIVE_SKIP = 10
    }
}
