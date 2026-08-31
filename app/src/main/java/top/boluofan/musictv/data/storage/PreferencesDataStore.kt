package top.boluofan.musictv.data.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import top.boluofan.musictv.domain.KeyMapping
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore by preferencesDataStore(name = "music_tv_settings")

@Singleton
class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val themeMode: Flow<Int> = context.dataStore.data.map { it[THEME_MODE] ?: 0 }
    val themeColor: Flow<String> = context.dataStore.data.map { it[THEME_COLOR] ?: "indigo" }
    val serverUrl: Flow<String?> = context.dataStore.data.map { it[SERVER_URL] }
    val username: Flow<String?> = context.dataStore.data.map { it[USERNAME] }
    val token: Flow<String?> = context.dataStore.data.map { it[TOKEN] }
    val quality: Flow<String> = context.dataStore.data.map { it[QUALITY] ?: "320k" }
    val backgroundPlay: Flow<Boolean> = context.dataStore.data.map { it[BACKGROUND_PLAY] ?: true }
    val autoResumeOnLaunch: Flow<Boolean> = context.dataStore.data.map { it[AUTO_RESUME_ON_LAUNCH] ?: false }
    val autoOpenPlayerOnLaunch: Flow<Boolean> = context.dataStore.data.map { it[AUTO_OPEN_PLAYER_ON_LAUNCH] ?: false }
    val playerControlsPersistent: Flow<Boolean> = context.dataStore.data.map { it[PLAYER_CONTROLS_PERSISTENT] ?: false }
    // 屏保等待时间：分钟，0 = 关闭（默认）
    val screensaverTimeoutMinutes: Flow<Int> = context.dataStore.data.map { it[SCREENSAVER_TIMEOUT_MINUTES] ?: 0 }
    val playMode: Flow<String> = context.dataStore.data.map { it[PLAY_MODE] ?: "ORDER" }
    val ignoredVersionCode: Flow<Int> = context.dataStore.data.map { it[IGNORED_VERSION_CODE] ?: 0 }
    // 首次启动已展示版权/免责声明（任意方式关闭即视为已展示，不再弹出）
    val disclaimerShown: Flow<Boolean> = context.dataStore.data.map { it[DISCLAIMER_SHOWN] ?: false }
    val keyMapping: Flow<KeyMapping> = context.dataStore.data.map {
        KeyMapping(
            up = it[KEY_MAPPING_UP] ?: 0,
            down = it[KEY_MAPPING_DOWN] ?: 0,
            left = it[KEY_MAPPING_LEFT] ?: 0,
            right = it[KEY_MAPPING_RIGHT] ?: 0,
            back = it[KEY_MAPPING_BACK] ?: 0,
            confirm = it[KEY_MAPPING_CONFIRM] ?: 0,
            top = it[KEY_MAPPING_TOP] ?: 0,
            bottom = it[KEY_MAPPING_BOTTOM] ?: 0,
            accompaniment = it[KEY_MAPPING_ACCOMPANIMENT] ?: 0
        )
    }

    // 音效（均衡器 + 音效模式）：总开关 / 预设 key / 增益(dB逗号串) / 音效模式 key / 强度 0-100
    val eqEnabled: Flow<Boolean> = context.dataStore.data.map { it[EQ_ENABLED] ?: false }
    val eqPreset: Flow<String> = context.dataStore.data.map { it[EQ_PRESET] ?: "flat" }
    val eqBands: Flow<String> = context.dataStore.data.map { it[EQ_BANDS] ?: "" }
    val sfxEnabled: Flow<Boolean> = context.dataStore.data.map { it[SFX_ENABLED] ?: false }
    val sfxMode: Flow<String> = context.dataStore.data.map { it[SFX_MODE] ?: "virtualizer" }
    val sfxStrength: Flow<Int> = context.dataStore.data.map { it[SFX_STRENGTH] ?: 50 }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setThemeColor(name: String) {
        context.dataStore.edit { it[THEME_COLOR] = name }
    }

    suspend fun setAuth(serverUrl: String, username: String, token: String) {
        context.dataStore.edit {
            it[SERVER_URL] = serverUrl
            it[USERNAME] = username
            it[TOKEN] = token
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit {
            it.remove(USERNAME)
            it.remove(TOKEN)
        }
    }

    /** 清除服务器配置与登录状态（设置页「清除配置」） */
    suspend fun clearServerConfig() {
        context.dataStore.edit {
            it.remove(SERVER_URL)
            it.remove(USERNAME)
            it.remove(TOKEN)
        }
    }

    suspend fun setQuality(quality: String) {
        context.dataStore.edit { it[QUALITY] = quality }
    }

    suspend fun setBackgroundPlay(enabled: Boolean) {
        context.dataStore.edit { it[BACKGROUND_PLAY] = enabled }
    }

    suspend fun setAutoResumeOnLaunch(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_RESUME_ON_LAUNCH] = enabled }
    }

    suspend fun setAutoOpenPlayerOnLaunch(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_OPEN_PLAYER_ON_LAUNCH] = enabled }
    }

    suspend fun setPlayerControlsPersistent(enabled: Boolean) {
        context.dataStore.edit { it[PLAYER_CONTROLS_PERSISTENT] = enabled }
    }

    suspend fun setScreensaverTimeoutMinutes(minutes: Int) {
        context.dataStore.edit { it[SCREENSAVER_TIMEOUT_MINUTES] = minutes }
    }

    suspend fun setPlayMode(mode: String) {
        context.dataStore.edit { it[PLAY_MODE] = mode }
    }

    suspend fun setIgnoredVersionCode(code: Int) {
        context.dataStore.edit { it[IGNORED_VERSION_CODE] = code }
    }

    suspend fun setDisclaimerShown() {
        context.dataStore.edit { it[DISCLAIMER_SHOWN] = true }
    }

    suspend fun setKeyMapping(mapping: KeyMapping) {
        context.dataStore.edit {
            it[KEY_MAPPING_UP] = mapping.up
            it[KEY_MAPPING_DOWN] = mapping.down
            it[KEY_MAPPING_LEFT] = mapping.left
            it[KEY_MAPPING_RIGHT] = mapping.right
            it[KEY_MAPPING_BACK] = mapping.back
            it[KEY_MAPPING_CONFIRM] = mapping.confirm
            it[KEY_MAPPING_TOP] = mapping.top
            it[KEY_MAPPING_BOTTOM] = mapping.bottom
            it[KEY_MAPPING_ACCOMPANIMENT] = mapping.accompaniment
        }
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[EQ_ENABLED] = enabled }
    }

    suspend fun setEqPreset(preset: String) {
        context.dataStore.edit { it[EQ_PRESET] = preset }
    }

    suspend fun setEqBands(bands: String) {
        context.dataStore.edit { it[EQ_BANDS] = bands }
    }

    suspend fun setSfxEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SFX_ENABLED] = enabled }
    }

    suspend fun setSfxMode(mode: String) {
        context.dataStore.edit { it[SFX_MODE] = mode }
    }

    suspend fun setSfxStrength(strength: Int) {
        context.dataStore.edit { it[SFX_STRENGTH] = strength }
    }

    /** 一次性快照，用于启动时同步 ApiClient 拦截器 */
    suspend fun authSnapshot(): AuthSnapshot {
        val data = context.dataStore.data.first()
        return AuthSnapshot(
            serverUrl = data[SERVER_URL],
            username = data[USERNAME],
            token = data[TOKEN]
        )
    }

    data class AuthSnapshot(
        val serverUrl: String?,
        val username: String?,
        val token: String?
    )

    companion object {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val THEME_COLOR = stringPreferencesKey("theme_color")
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val USERNAME = stringPreferencesKey("username")
        private val TOKEN = stringPreferencesKey("token")
        private val QUALITY = stringPreferencesKey("quality")
        private val BACKGROUND_PLAY = booleanPreferencesKey("background_play")
        private val AUTO_RESUME_ON_LAUNCH = booleanPreferencesKey("auto_resume_on_launch")
        private val AUTO_OPEN_PLAYER_ON_LAUNCH = booleanPreferencesKey("auto_open_player_on_launch")
        private val PLAYER_CONTROLS_PERSISTENT = booleanPreferencesKey("player_controls_persistent")
        private val SCREENSAVER_TIMEOUT_MINUTES = intPreferencesKey("screensaver_timeout_minutes")
        private val PLAY_MODE = stringPreferencesKey("play_mode")
        private val IGNORED_VERSION_CODE = intPreferencesKey("ignored_version_code")
        private val DISCLAIMER_SHOWN = booleanPreferencesKey("disclaimer_shown")
        private val KEY_MAPPING_UP = intPreferencesKey("key_mapping_up")
        private val KEY_MAPPING_DOWN = intPreferencesKey("key_mapping_down")
        private val KEY_MAPPING_LEFT = intPreferencesKey("key_mapping_left")
        private val KEY_MAPPING_RIGHT = intPreferencesKey("key_mapping_right")
        private val KEY_MAPPING_BACK = intPreferencesKey("key_mapping_back")
        private val KEY_MAPPING_CONFIRM = intPreferencesKey("key_mapping_confirm")
        private val KEY_MAPPING_TOP = intPreferencesKey("key_mapping_top")
        private val KEY_MAPPING_BOTTOM = intPreferencesKey("key_mapping_bottom")
        private val KEY_MAPPING_ACCOMPANIMENT = intPreferencesKey("key_mapping_accompaniment")

        private val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        private val EQ_PRESET = stringPreferencesKey("eq_preset")
        private val EQ_BANDS = stringPreferencesKey("eq_bands")
        private val SFX_ENABLED = booleanPreferencesKey("sfx_enabled")
        private val SFX_MODE = stringPreferencesKey("sfx_mode")
        private val SFX_STRENGTH = intPreferencesKey("sfx_strength")
    }
}
