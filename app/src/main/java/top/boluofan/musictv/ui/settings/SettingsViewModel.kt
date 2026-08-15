package top.boluofan.musictv.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.api.ApiClient
import top.boluofan.musictv.data.storage.PreferencesDataStore
import top.boluofan.musictv.domain.KeyMapping
import top.boluofan.musictv.domain.MappingTarget
import top.boluofan.musictv.domain.PlayerController
import top.boluofan.musictv.util.LogStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: Int = 0,
    val themeColor: String = "indigo",
    val serverUrl: String = "",
    val audioQuality: String = "320k",
    val backgroundPlayback: Boolean = true,
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemaining: Int = 0,
    val sleepAfterSongs: Int = 0,
    val sleepAfterSongsRemaining: Int = 0,
    val logExportStatus: String = "",
    val keyMapping: KeyMapping = KeyMapping()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: PreferencesDataStore,
    private val playerController: PlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.themeMode.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
        viewModelScope.launch {
            dataStore.themeColor.collect { name ->
                _uiState.value = _uiState.value.copy(themeColor = name)
            }
        }
        viewModelScope.launch {
            dataStore.serverUrl.collect { url ->
                _uiState.value = _uiState.value.copy(serverUrl = url ?: "")
            }
        }
        viewModelScope.launch {
            dataStore.quality.collect { q ->
                _uiState.value = _uiState.value.copy(audioQuality = q)
            }
        }
        viewModelScope.launch {
            dataStore.backgroundPlay.collect { enabled ->
                _uiState.value = _uiState.value.copy(backgroundPlayback = enabled)
            }
        }
        viewModelScope.launch {
            dataStore.keyMapping.collect { mapping ->
                _uiState.value = _uiState.value.copy(keyMapping = mapping)
            }
        }
        viewModelScope.launch {
            playerController.state.collect { s ->
                _uiState.value = _uiState.value.copy(
                    sleepTimerMinutes = s.sleepTimerMinutes,
                    sleepTimerRemaining = s.sleepTimerRemaining,
                    sleepAfterSongs = s.sleepAfterSongs,
                    sleepAfterSongsRemaining = s.sleepAfterSongsRemaining
                )
            }
        }
    }

    fun setSleepTimer(minutes: Int) {
        playerController.setSleepTimer(minutes)
    }

    fun setSleepAfterSongs(count: Int) {
        playerController.setSleepAfterSongs(count)
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { dataStore.setThemeMode(mode) }
    }

    fun setThemeColor(name: String) {
        viewModelScope.launch { dataStore.setThemeColor(name) }
    }

    fun setAudioQuality(quality: String) {
        viewModelScope.launch { dataStore.setQuality(quality) }
    }

    fun setBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch { dataStore.setBackgroundPlay(enabled) }
    }

    fun setKeyMapping(target: MappingTarget, keyCode: Int) {
        viewModelScope.launch {
            val current = _uiState.value.keyMapping
            dataStore.setKeyMapping(
                when (target) {
                    MappingTarget.UP -> current.copy(up = keyCode)
                    MappingTarget.DOWN -> current.copy(down = keyCode)
                    MappingTarget.LEFT -> current.copy(left = keyCode)
                    MappingTarget.RIGHT -> current.copy(right = keyCode)
                    MappingTarget.BACK -> current.copy(back = keyCode)
                    MappingTarget.CONFIRM -> current.copy(confirm = keyCode)
                    MappingTarget.TOP -> current.copy(top = keyCode)
                    MappingTarget.BOTTOM -> current.copy(bottom = keyCode)
                }
            )
        }
    }

    fun resetKeyMapping() {
        viewModelScope.launch { dataStore.setKeyMapping(KeyMapping()) }
    }

    // 重启应用：先发启动 Intent 再退出进程，由系统重新拉起
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    fun clearServerConfig() {
        viewModelScope.launch {
            dataStore.clearServerConfig()
            ApiClient.authInterceptor.username = null
            ApiClient.authInterceptor.token = null
        }
    }

    fun exportLogs() {
        _uiState.value = _uiState.value.copy(logExportStatus = "正在导出…")
        viewModelScope.launch(Dispatchers.IO) {
            val status = runCatching {
                val fileName = "music-tv-log-" +
                    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
                val process = Runtime.getRuntime()
                    .exec(arrayOf("logcat", "-d", "-v", "threadtime"))
                val file = File(LogStore.dir(context), fileName)
                process.inputStream.bufferedReader().use { reader ->
                    file.bufferedWriter().use { writer ->
                        reader.forEachLine { writer.appendLine(sanitizeLogLine(it)) }
                    }
                }
                "已导出 $fileName（已脱敏），手机扫码页面「日志」页签可下载"
            }.getOrElse { e -> "导出失败：${e.message}" }
            _uiState.value = _uiState.value.copy(logExportStatus = status)
        }
    }

    private fun sanitizeLogLine(line: String): String {
        var s = line
        // HTTP 头：Authorization / Cookie / Set-Cookie
        s = s.replace(SENSITIVE_HEADER_REGEX, "$1: ***")
        // JSON 字段：token / password / secret 等
        s = s.replace(SENSITIVE_JSON_REGEX, "$1***$2")
        // URL 参数或 key=value 形式的 token / password
        s = s.replace(SENSITIVE_PARAM_REGEX, "$1***")
        // 裸 JWT
        s = s.replace(JWT_REGEX, "***.***.***")
        return s
    }

    private companion object {
        val SENSITIVE_HEADER_REGEX =
            Regex("(?i)\\b(authorization|cookie|set-cookie|x-api-key)\\s*:\\s*.*")
        val SENSITIVE_JSON_REGEX =
            Regex("(?i)(\"(?:access_token|refresh_token|token|password|secret)\"\\s*:\\s*\")[^\"]*(\")")
        val SENSITIVE_PARAM_REGEX =
            Regex("(?i)\\b((?:access_token|refresh_token|token|password|secret)=)[^&\\s\"']+")
        val JWT_REGEX =
            Regex("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b")
    }
}
