package top.boluofan.musictv.domain

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val sleepAfterSongsRemaining: Int = 0
)

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: PreferencesDataStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // 播放模式持久化闭环：重启后恢复上次模式，并在控制器已连接时即时重放
        scope.launch {
            dataStore.playMode.collect { mode ->
                val playMode = runCatching { PlayMode.valueOf(mode) }.getOrDefault(PlayMode.ORDER)
                _state.update { it.copy(playMode = playMode) }
                controller?.let { applyPlayMode(it, playMode) }
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
