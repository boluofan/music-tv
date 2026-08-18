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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.ln
import kotlin.math.round
import top.boluofan.musictv.MusicService
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.storage.PreferencesDataStore
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
    val sfxActiveMode: String = "off"
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
    private val dataStore: PreferencesDataStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var eqEnabledCache: Boolean = false
    private var eqBandsCache: List<Int> = emptyList()

    private var sfxEnabledCache: Boolean = false
    private var sfxModeCache: String = "virtualizer"
    private var sfxStrengthCache: Int = 50

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
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previousSong = _state.value.currentSong
            val song = _state.value.queue.firstOrNull { mediaKey(it) == mediaItem?.mediaId }
            countDownSleepAfterSongs(previousSong, song, reason)
            _state.update {
                it.copy(
                    currentSong = song,
                    currentIndex = controller?.currentMediaItemIndex ?: -1,
                    duration = (song?.interval?.toLongOrNull() ?: 0L) * 1000
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
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
            }
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
                }
                action(c)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun play(queue: List<MusicInfo>, index: Int) {
        val song = queue.getOrNull(index) ?: return
        _state.update {
            it.copy(
                queue = queue,
                currentIndex = index,
                currentSong = song,
                duration = (song.interval?.toLongOrNull() ?: 0L) * 1000
            )
        }
        withController { c ->
            c.setMediaItems(queue.map { buildMediaItem(it) }, index, 0L)
            applyPlayMode(c, _state.value.playMode)
            c.prepare()
            c.play()
        }
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else c.play()
    }

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

    companion object {
        private const val TAG = "PlayerController"
    }
}
