package top.boluofan.musictv

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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

    // 播放地址缓存：同一首歌重复播放不再请求 /api/music/url
    private val urlCache = ConcurrentHashMap<String, String>()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

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

        val sessionActivityIntent = Intent(this, PlayerActivity::class.java)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionActivityPendingIntent)
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
