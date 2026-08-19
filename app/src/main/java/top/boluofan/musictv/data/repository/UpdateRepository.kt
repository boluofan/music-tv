package top.boluofan.musictv.data.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.boluofan.musictv.BuildConfig
import top.boluofan.musictv.data.api.TlsCompat
import top.boluofan.musictv.data.model.UpdateInfo
import top.boluofan.musictv.data.model.VersionJson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

sealed interface DownloadState {
    data class Downloading(
        val bytesRead: Long,
        val totalBytes: Long,
        val mirrorLabel: String
    ) : DownloadState

    data class Success(val apkFile: File) : DownloadState
    data class Failed(val message: String) : DownloadState
}

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "UpdateRepository"
        private const val VERSION_JSON_URL =
            "https://github.com/boluofan/music-tv/releases/latest/download/version.json"
        private const val APK_URL =
            "https://github.com/boluofan/music-tv/releases/latest/download/music-tv.apk"

        // 前缀代理池，空串 = 直连；检查并发请求、下载先测速排序，不再按固定顺序回退
        private val MIRRORS = listOf(
            "" to "GitHub 直连",
            "https://ghfast.top/" to "ghfast.top",
            "https://gh-proxy.com/" to "gh-proxy.com",
            "https://ghproxy.net/" to "ghproxy.net"
        )

        // 下载前并发拉取各镜像 APK 前 64KB 测速，排序后只从最优镜像整包下载
        private const val PROBE_BYTES = 64 * 1024
        // 稳定度打分所需最小样本数，样本不足视为未知（满分）
        private const val MIN_STABILITY_SAMPLES = 2
    }

    // 进程内启动自动检查只执行一次
    var autoCheckDone = false

    private val gson = Gson()

    private val checkClient: OkHttpClient = TlsCompat.apply(OkHttpClient.Builder())
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val downloadClient: OkHttpClient = TlsCompat.apply(OkHttpClient.Builder())
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val probeClient: OkHttpClient = TlsCompat.apply(OkHttpClient.Builder())
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    // 进程内各镜像历史成功/失败次数，用于稳定度排序（进程重启清零，够用即可）
    private class MirrorStat(var success: Int = 0, var fail: Int = 0)
    private val mirrorStats = mutableMapOf<String, MirrorStat>()

    private fun recordSuccess(label: String) {
        synchronized(mirrorStats) {
            mirrorStats.getOrPut(label) { MirrorStat() }.success++
        }
    }

    private fun recordFailure(label: String) {
        synchronized(mirrorStats) {
            mirrorStats.getOrPut(label) { MirrorStat() }.fail++
        }
    }

    private fun stability(label: String): Double = synchronized(mirrorStats) {
        val stat = mirrorStats[label] ?: return 1.0
        val total = stat.success + stat.fail
        if (total >= MIN_STABILITY_SAMPLES) stat.success.toDouble() / total else 1.0
    }

    private sealed interface CheckOutcome {
        data class Ok(val json: VersionJson, val latencyMs: Long, val label: String) : CheckOutcome
        data object AllFailed : CheckOutcome
    }

    suspend fun checkUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        // 并发请求全部镜像，取延迟最低的成功结果（每个请求有 10s 总超时上限，总耗时≈最慢请求）
        val outcome = coroutineScope {
            val deferreds = MIRRORS.map { (prefix, label) -> async { checkMirror(prefix, label) } }
            val results = deferreds.awaitAll().filterNotNull()
            ensureActive()
            results.minByOrNull { it.latencyMs } ?: CheckOutcome.AllFailed
        }
        when (outcome) {
            is CheckOutcome.Ok -> {
                Log.i(TAG, "更新检查命中镜像（${outcome.label}）${outcome.latencyMs}ms")
                evaluate(outcome.json)
            }
            CheckOutcome.AllFailed -> UpdateCheckResult.Failed("检查更新失败，网络不可用！")
        }
    }

    private suspend fun checkMirror(prefix: String, label: String): CheckOutcome.Ok? {
        val start = SystemClock.elapsedRealtime()
        val call = checkClient.newCall(Request.Builder().url(prefix + VERSION_JSON_URL).build())
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        return try {
            Log.i(TAG, "检查更新: ${prefix + VERSION_JSON_URL}")
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val json = gson.fromJson(response.body!!.string(), VersionJson::class.java)
                recordSuccess(label)
                CheckOutcome.Ok(json, SystemClock.elapsedRealtime() - start, label)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "检查更新失败（$label）", e)
            recordFailure(label)
            null
        } finally {
            handle.dispose()
        }
    }

    private fun evaluate(json: VersionJson): UpdateCheckResult {
        val remoteCode = json.versionCode
        if (remoteCode != null) {
            return if (remoteCode > BuildConfig.VERSION_CODE) {
                UpdateCheckResult.UpdateAvailable(
                    UpdateInfo(
                        versionCode = remoteCode,
                        versionName = json.version ?: remoteCode.toString(),
                        apkUrl = APK_URL,
                        releaseNotes = json.releaseNotes
                    )
                )
            } else {
                UpdateCheckResult.UpToDate
            }
        }

        // 旧 Release 无 version_code，回退到版本名（semver）比较
        val remoteName = json.version ?: return UpdateCheckResult.UpToDate
        val remote = parseSemver(remoteName) ?: return UpdateCheckResult.UpToDate
        val local = parseSemver(BuildConfig.VERSION_NAME) ?: return UpdateCheckResult.UpToDate
        return if (compareSemver(remote, local) > 0) {
            UpdateCheckResult.UpdateAvailable(
                UpdateInfo(
                    // 合成版本号，保证忽略过滤与缓存文件命名可用
                    versionCode = remote[0] * 1_000_000 + remote[1] * 1_000 + remote[2],
                    versionName = remoteName,
                    apkUrl = APK_URL,
                    releaseNotes = json.releaseNotes
                )
            )
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    private fun parseSemver(name: String): List<Int>? {
        val parts = name.trim().removePrefix("v").split(".")
        if (parts.isEmpty() || parts.size > 3) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        return List(3) { nums.getOrElse(it) { 0 } }
    }

    private fun compareSemver(a: List<Int>, b: List<Int>): Int {
        for (i in 0..2) {
            if (a[i] != b[i]) return a[i] - b[i]
        }
        return 0
    }

    fun downloadApk(info: UpdateInfo): Flow<DownloadState> = flow {
        val dir = File(context.cacheDir, "updates")
        val target = File(dir, "music-tv-${info.versionCode}.apk")

        // 并发探测各镜像网速，按（网速、历史稳定度、延迟）排序后整包下载；
        // 探测全部失败则退回原顺序逐个尝试
        val ranked = probeMirrors(info.apkUrl).ifEmpty { MIRRORS }

        for ((prefix, label) in ranked) {
            currentCoroutineContext().ensureActive()
            try {
                downloadFrom(prefix + info.apkUrl, label, dir, target)
                recordSuccess(label)
                emit(DownloadState.Success(target))
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordFailure(label)
                Log.w(TAG, "下载失败（$label）", e)
            }
        }
        emit(DownloadState.Failed("网络不可用，请手动下载安装！"))
    }.flowOn(Dispatchers.IO)

    private data class Probe(
        val prefix: String,
        val label: String,
        val latencyMs: Long,
        val speedBps: Long
    )

    private suspend fun probeMirrors(apkUrl: String): List<Pair<String, String>> = coroutineScope {
        MIRRORS.map { (prefix, label) -> async { probeOne(prefix, apkUrl, label) } }
            .awaitAll()
            .filterNotNull()
            .sortedWith(
                compareByDescending<Probe> { it.speedBps }
                    .thenByDescending { stability(it.label) }
                    .thenBy { it.latencyMs }
            )
            .map { it.prefix to it.label }
    }

    private suspend fun probeOne(prefix: String, apkUrl: String, label: String): Probe? {
        val start = SystemClock.elapsedRealtime()
        val url = prefix + apkUrl
        val call = probeClient.newCall(
            Request.Builder().url(url).header("Range", "bytes=0-${PROBE_BYTES - 1}").build()
        )
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        return try {
            Log.i(TAG, "镜像探测: $url")
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val elapsed = SystemClock.elapsedRealtime() - start
                val body = response.body ?: throw IOException("响应体为空")
                val buf = ByteArray(16 * 1024)
                var read = 0
                while (read < PROBE_BYTES) {
                    val n = body.byteStream().read(buf, 0, minOf(buf.size, PROBE_BYTES - read))
                    if (n < 0) break
                    read += n
                }
                // 服务器可能忽略 Range 返回全量，读满探测窗口即断开，不影响排序
                val speed = read * 1000L / elapsed.coerceAtLeast(1)
                Log.i(TAG, "镜像探测（$label）: ${read}B / ${elapsed}ms = ${speed}B/s")
                Probe(prefix, label, elapsed, speed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "镜像探测失败（$label）", e)
            null
        } finally {
            handle.dispose()
        }
    }

    private suspend fun FlowCollector<DownloadState>.downloadFrom(
        url: String,
        label: String,
        dir: File,
        target: File
    ) {
        val call = downloadClient.newCall(Request.Builder().url(url).build())
        Log.i(TAG, "下载 APK: $url")
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("响应体为空")
                val total = body.contentLength()

                // 命中已完整下载的文件直接复用
                if (target.exists() && total > 0 && target.length() == total) {
                    return
                }

                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val tmp = File(dir, target.name + ".tmp")

                var read = 0L
                emit(DownloadState.Downloading(0L, total, label))
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var lastEmit = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            // 每 256KB 上报一次进度，避免高频重组
                            if (read - lastEmit >= 256 * 1024) {
                                lastEmit = read
                                emit(DownloadState.Downloading(read, total, label))
                            }
                        }
                    }
                }
                if (total > 0 && read != total) throw IOException("下载不完整：$read/$total")
                if (!tmp.renameTo(target)) throw IOException("重命名失败")
                emit(DownloadState.Downloading(read, total, label))
            }
        } finally {
            handle.dispose()
        }
    }
}
