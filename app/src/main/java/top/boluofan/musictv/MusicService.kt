package top.boluofan.musictv

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import top.boluofan.musictv.data.api.ApiClient
import top.boluofan.musictv.data.storage.PreferencesDataStore
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class MusicService : MediaSessionService() {

    @Inject lateinit var dataStore: PreferencesDataStore

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private val sfxEffects = mutableMapOf<SfxType, AudioEffect>()

    private enum class SfxType { VIRTUALIZER, BASS_BOOST, LOUDNESS, REVERB }

    // 播放地址缓存：同一首歌重复播放不再请求 /api/music/url
    private val urlCache = ConcurrentHashMap<String, String>()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            onOutputDevicesChanged()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            onOutputDevicesChanged()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 输出设备切换（HDMI/蓝牙/内置喇叭）后旧效果绑定可能失效，先释放，由下一次 sfx/info 重新校验
        // AudioDeviceCallback/getDevices 为 API 23+，低版本设备跳过监听（音效能力按连接时查询为准）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                (getSystemService(AUDIO_SERVICE) as AudioManager)
                    .registerAudioDeviceCallback(audioDeviceCallback, null)
            }
        }

        val quality = runCatching { runBlocking { dataStore.quality.first() } }.getOrDefault("320k")

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(USER_AGENT)

        // 队列中的歌曲以 lxmusic://resolve 占位 URI 入队，播放到该曲时才解析真实地址
        val resolvingFactory = ResolvingDataSource.Factory(
            httpDataSourceFactory
        ) { dataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme != RESOLVE_SCHEME || uri.host != RESOLVE_HOST) return@Factory dataSpec
            val source = uri.getQueryParameter("source")
            val songmid = uri.getQueryParameter("songmid")
            if (source.isNullOrEmpty() || songmid.isNullOrEmpty()) {
                throw IOException("resolve 参数缺失：source=$source songmid=$songmid")
            }
            val cacheKey = "$source:$songmid"
            val resolved = urlCache[cacheKey] ?: resolveMusicUrl(
                source, songmid, uri.getQueryParameter("name"), quality
            ) ?: throw IOException("获取播放地址失败：${uri.getQueryParameter("name")}")
            urlCache[cacheKey] = resolved
            dataSpec.withUri(Uri.parse(resolved))
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(resolvingFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(playerListener) }

        val sessionActivityIntent = Intent(this, PlayerActivity::class.java)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(mediaSessionCallback)
            .build()
    }

    /** 请求 lxserver 获取播放地址；ApiClient 拦截器自动附加 x-user-name/x-user-token */
    private fun resolveMusicUrl(source: String, songmid: String, name: String?, quality: String): String? {
        return runCatching {
            val songInfo = HashMap<String, Any?>()
            songInfo["source"] = source
            songInfo["songmid"] = songmid
            if (!name.isNullOrBlank()) songInfo["name"] = name
            runBlocking {
                ApiClient.getMusicApi().getMusicUrl(
                    mapOf(
                        "songInfo" to songInfo,
                        "quality" to quality,
                        "enableAutoSwitchApiSource" to true
                    )
                )
            }.url
        }.onFailure { Log.e(TAG, "解析播放地址失败：source=$source songmid=$songmid", it) }
            .getOrNull()
    }

    // ===== 均衡器 / 音效（audiofx）引擎 =====

    // 均衡器按需创建：ExoPlayer 构建时即分配 audioSessionId，无需等待播放开始；
    // 失败（如设备无 audiofx HAL）时每次命令到达都会重试
    private fun ensureEqualizer(): Equalizer? {
        equalizer?.let { return it }
        val sessionId = player?.audioSessionId ?: return null
        if (sessionId <= 0) return null
        return try {
            Equalizer(0, sessionId).also {
                equalizer = it
                Log.d(TAG, "均衡器创建成功：${it.numberOfBands} 段，${it.numberOfPresets} 个预设（session=$sessionId）")
            }
        } catch (e: Exception) {
            Log.w(TAG, "创建均衡器失败，audioSession=$sessionId", e)
            null
        }
    }

    private fun ensureSfx(type: SfxType): AudioEffect? {
        sfxEffects[type]?.let { return it }
        val sessionId = player?.audioSessionId ?: return null
        if (sessionId <= 0) return null
        return try {
            val effect: AudioEffect = when (type) {
                SfxType.VIRTUALIZER -> Virtualizer(0, sessionId)
                SfxType.BASS_BOOST -> BassBoost(0, sessionId)
                SfxType.LOUDNESS -> LoudnessEnhancer(sessionId)
                SfxType.REVERB -> PresetReverb(0, sessionId)
            }
            sfxEffects[type] = effect
            Log.d(TAG, "音效效果器创建成功：$type（session=$sessionId）")
            effect
        } catch (e: Exception) {
            Log.w(TAG, "创建音效效果器失败：$type，audioSession=$sessionId", e)
            null
        }
    }

    // 单模式互斥：先禁用全部效果，再启用选中的模式；"off" 只做禁用
    private fun applySfx(mode: String, strength: Int): Boolean {
        val type = when (mode) {
            "virtualizer" -> SfxType.VIRTUALIZER
            "bass_boost" -> SfxType.BASS_BOOST
            "loudness" -> SfxType.LOUDNESS
            "reverb" -> SfxType.REVERB
            else -> null
        }
        if (type == null) {
            runCatching { sfxEffects.values.forEach { it.enabled = false } }
            Log.d(TAG, "sfx/apply：mode=off，全部效果已禁用")
            return true
        }
        val ok = runCatching {
            sfxEffects.values.forEach { it.enabled = false }
            val effect = ensureSfx(type) ?: return@runCatching false
            mapSfxStrength(type, effect, strength)
            effect.enabled = true
            true
        }.getOrDefault(false)
        Log.d(TAG, "sfx/apply 结果：$ok（mode=$mode, strength=$strength）")
        return ok
    }

    // 语义强度 0-100 → audiofx 参数；BassBoost 上限 600 防破音，PresetReverb 无强度参数按段映射
    private fun mapSfxStrength(type: SfxType, effect: AudioEffect, strength: Int) {
        val s = strength.coerceIn(0, 100)
        when (type) {
            SfxType.VIRTUALIZER -> (effect as Virtualizer).setStrength((s * 10).toShort())
            SfxType.BASS_BOOST -> (effect as BassBoost).setStrength((s * 10).coerceAtMost(600).toShort())
            SfxType.LOUDNESS -> (effect as LoudnessEnhancer).setTargetGain(s * 20)
            SfxType.REVERB -> (effect as PresetReverb).setPreset(
                when {
                    s < 34 -> PresetReverb.PRESET_MEDIUMROOM
                    s <= 66 -> PresetReverb.PRESET_LARGEROOM
                    else -> PresetReverb.PRESET_LARGEHALL
                }
            )
        }
    }

    // 静态能力矩阵：不依赖音频会话；null 视为全不支持（与 eq/check 一致）
    private fun querySfxMatrix(): Map<SfxType, Boolean> {
        val effects = AudioEffect.queryEffects()
        return SfxType.entries.associateWith { type ->
            val typeUuid = when (type) {
                SfxType.VIRTUALIZER -> AudioEffect.EFFECT_TYPE_VIRTUALIZER
                SfxType.BASS_BOOST -> AudioEffect.EFFECT_TYPE_BASS_BOOST
                SfxType.LOUDNESS -> AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER
                SfxType.REVERB -> AudioEffect.EFFECT_TYPE_PRESET_REVERB
            }
            effects?.any { it.type == typeUuid } == true
        }
    }

    private fun releaseSfxAll() {
        sfxEffects.values.forEach { runCatching { it.release() } }
        sfxEffects.clear()
    }

    private fun isA2dpActive(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return (getSystemService(AUDIO_SERVICE) as? AudioManager)
            ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } == true
    }

    private fun onOutputDevicesChanged() {
        Log.d(TAG, "输出设备变化（A2DP=${isA2dpActive()}），音效效果已停用，待重新校验")
        releaseSfxAll()
    }

    // 音频会话 id 变化时旧均衡器失效，释放后按需重建
    private val playerListener = object : Player.Listener {
        @OptIn(UnstableApi::class)
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId <= 0) return
            equalizer?.release()
            equalizer = null
            releaseSfxAll()
            Log.d(TAG, "音频会话变化：$audioSessionId，均衡器/音效按需重建")
        }
    }

    @OptIn(UnstableApi::class)
    private val mediaSessionCallback = object : MediaSession.Callback {
        // 自定义命令必须在此授权，否则分发前被拒（ERROR_PERMISSION_DENIED）
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val available = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(EQ_APPLY, Bundle.EMPTY))
                .add(SessionCommand(EQ_INFO, Bundle.EMPTY))
                .add(SessionCommand(EQ_CHECK, Bundle.EMPTY))
                .add(SessionCommand(SFX_APPLY, Bundle.EMPTY))
                .add(SessionCommand(SFX_INFO, Bundle.EMPTY))
                .add(SessionCommand(SFX_CHECK, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(available)
                .build()
        }

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            // 音效命令不依赖均衡器，先行分发
            when (customCommand.customAction) {
                SFX_APPLY -> {
                    // enabled=false 时即使 mode 残留也强制关闭，避免"开关已关但效果仍生效"
                    val mode = if (args.getBoolean(EXTRA_ENABLED, false)) {
                        args.getString(EXTRA_MODE, "off")
                    } else {
                        "off"
                    }
                    val ok = applySfx(mode, args.getInt(EXTRA_STRENGTH, 50))
                    return Futures.immediateFuture(
                        if (ok) SessionResult(SessionResult.RESULT_SUCCESS)
                        else SessionResult(SessionResult.RESULT_ERROR_UNKNOWN)
                    )
                }
                SFX_INFO -> {
                    val matrix = querySfxMatrix()
                    val extras = Bundle().apply {
                        putBoolean(EXTRA_SUPPORTED, matrix.values.any { it })
                        putBooleanArray(
                            EXTRA_SUPPORTED_MATRIX,
                            SfxType.entries.map { matrix[it] == true }.toBooleanArray()
                        )
                        putBoolean(EXTRA_A2DP, isA2dpActive())
                        putString(
                            EXTRA_ACTIVE_MODE,
                            sfxEffects.entries.firstOrNull { it.value.enabled }?.key?.name?.lowercase() ?: "off"
                        )
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, extras))
                }
                SFX_CHECK -> {
                    val supported = querySfxMatrix().values.any { it }
                    Log.d(TAG, "sfx/check：设备支持音效 = $supported")
                    return Futures.immediateFuture(
                        SessionResult(
                            SessionResult.RESULT_SUCCESS,
                            Bundle().apply { putBoolean(EXTRA_SUPPORTED, supported) }
                        )
                    )
                }
            }
            val eq = ensureEqualizer()
            if (eq == null) {
                Log.w(TAG, "收到 ${customCommand.customAction} 但均衡器不可用（未创建或创建失败）")
                return if (customCommand.customAction == EQ_INFO) {
                    Futures.immediateFuture(
                        SessionResult(
                            SessionResult.RESULT_SUCCESS,
                            Bundle().apply { putBoolean(EXTRA_SUPPORTED, false) }
                        )
                    )
                } else {
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
                }
            }
            return Futures.immediateFuture(
                when (customCommand.customAction) {
                    EQ_APPLY -> {
                        val ok = runCatching {
                            eq.enabled = args.getBoolean(EXTRA_ENABLED, false)
                            args.getIntArray(EXTRA_BANDS)?.let { bands ->
                                val count = minOf(bands.size, eq.numberOfBands.toInt())
                                val range = eq.bandLevelRange
                                for (i in 0 until count) {
                                    val level = (bands[i] * 100)
                                        .coerceIn(range[0].toInt(), range[1].toInt())
                                        .toShort()
                                    eq.setBandLevel(i.toShort(), level)
                                }
                            }
                        }.isSuccess
                        Log.d(TAG, "eq/apply 结果：$ok（enabled=${args.getBoolean(EXTRA_ENABLED, false)}）")
                        if (ok) SessionResult(SessionResult.RESULT_SUCCESS)
                        else SessionResult(SessionResult.RESULT_ERROR_UNKNOWN)
                    }
                    EQ_INFO -> {
                        Log.d(TAG, "eq/info 返回：${eq.numberOfBands} 段")
                        val extras = Bundle().apply {
                            putBoolean(EXTRA_SUPPORTED, true)
                            val bandCount = eq.numberOfBands.toInt()
                            putInt(EXTRA_BAND_COUNT, bandCount)
                            putIntArray(
                                EXTRA_CENTER_FREQS,
                                IntArray(bandCount) { eq.getCenterFreq(it.toShort()) }
                            )
                            val range = eq.bandLevelRange
                            putInt(EXTRA_LEVEL_MIN, range[0].toInt())
                            putInt(EXTRA_LEVEL_MAX, range[1].toInt())
                            putBoolean(EXTRA_ENABLED, eq.enabled)
                            putIntArray(
                                EXTRA_BANDS,
                                IntArray(bandCount) { eq.getBandLevel(it.toShort()).toInt() / 100 }
                            )
                        }
                        SessionResult(SessionResult.RESULT_SUCCESS, extras)
                    }
                    EQ_CHECK -> {
                        val supported = AudioEffect.queryEffects()
                            ?.any { it.type == AudioEffect.EFFECT_TYPE_EQUALIZER } == true
                        Log.d(TAG, "eq/check：设备支持均衡器 = $supported")
                        SessionResult(
                            SessionResult.RESULT_SUCCESS,
                            Bundle().apply { putBoolean(EXTRA_SUPPORTED, supported) }
                        )
                    }
                    else -> SessionResult(SessionResult.RESULT_ERROR_UNKNOWN)
                }
            )
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player != null && !player!!.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        equalizer?.release()
        equalizer = null
        releaseSfxAll()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                (getSystemService(AUDIO_SERVICE) as AudioManager)
                    .unregisterAudioDeviceCallback(audioDeviceCallback)
            }
        }
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    companion object {
        private const val TAG = "MusicService"
        private const val RESOLVE_SCHEME = "lxmusic"
        private const val RESOLVE_HOST = "resolve"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        const val EQ_APPLY = "eq/apply"
        const val EQ_INFO = "eq/info"
        const val EQ_CHECK = "eq/check"

        const val SFX_APPLY = "sfx/apply"
        const val SFX_INFO = "sfx/info"
        const val SFX_CHECK = "sfx/check"

        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_BANDS = "bands"
        const val EXTRA_SUPPORTED = "supported"
        const val EXTRA_BAND_COUNT = "bandCount"
        const val EXTRA_CENTER_FREQS = "centerFreqs"
        const val EXTRA_LEVEL_MIN = "levelMin"
        const val EXTRA_LEVEL_MAX = "levelMax"

        const val EXTRA_MODE = "mode"
        const val EXTRA_STRENGTH = "strength"
        const val EXTRA_SUPPORTED_MATRIX = "supportedMatrix"
        const val EXTRA_A2DP = "a2dp"
        const val EXTRA_ACTIVE_MODE = "activeMode"

        /** v1 兼容入口：构造占位 URI，实际解析在服务端 ResolvingDataSource 中完成 */
        @JvmStatic
        fun buildResolveUri(source: String?, songmid: String?, name: String?): Uri {
            val builder = Uri.Builder()
                .scheme(RESOLVE_SCHEME)
                .authority(RESOLVE_HOST)
                .appendQueryParameter("source", source ?: "")
                .appendQueryParameter("songmid", songmid ?: "")
            if (!name.isNullOrBlank()) {
                builder.appendQueryParameter("name", name)
            }
            return builder.build()
        }
    }
}
