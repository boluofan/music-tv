package top.boluofan.musictv.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.api.ApiClient
import top.boluofan.musictv.data.repository.AuthRepository
import top.boluofan.musictv.data.storage.PreferencesDataStore
import java.io.IOException
import javax.inject.Inject

sealed class AuthState {
    data object Loading : AuthState()
    data object NotConfigured : AuthState()
    data class LoggedIn(val username: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataStore: PreferencesDataStore
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _configUrl = MutableStateFlow<String?>(null)
    val configUrl: StateFlow<String?> = _configUrl.asStateFlow()

    private val _rememberMe = MutableStateFlow(false)
    val rememberMe: StateFlow<Boolean> = _rememberMe.asStateFlow()

    private var configServer: ConfigWebServer? = null

    fun startConfigServer() {
        if (configServer != null) return
        val ip = ConfigWebServer.localIpAddress() ?: return
        for (port in CONFIG_PORTS) {
            val server = ConfigWebServer(port) { serverUrl, username, password, rememberMe ->
                viewModelScope.launch {
                    _serverUrl.value = serverUrl
                    _username.value = username
                    _password.value = password
                    _rememberMe.value = rememberMe
                    login()
                }
            }
            if (runCatching { server.start() }.isSuccess) {
                configServer = server
                _configUrl.value = "http://$ip:$port"
                return
            }
        }
    }

    private fun stopConfigServer() {
        configServer?.stop()
        configServer = null
        _configUrl.value = null
    }

    /** 无协议前缀时按 https → http 顺序生成候选地址，探测出可用协议 */
    private fun candidateUrls(url: String): List<String> =
        if (url.startsWith("http://") || url.startsWith("https://")) listOf(url)
        else listOf("https://$url", "http://$url")

    init {
        viewModelScope.launch {
            val storedUrl = dataStore.serverUrl.first()
            if (!storedUrl.isNullOrEmpty()) {
                _serverUrl.value = storedUrl
                if (dataStore.rememberMe.first()) {
                    // 勾选了记住登录：回填账号和密码
                    _username.value = dataStore.username.first() ?: ""
                    _password.value = dataStore.password.first() ?: ""
                }
                if (authRepository.tryAutoLogin()) {
                    val name = dataStore.username.first()
                    _authState.value = AuthState.LoggedIn(name ?: "admin")
                } else {
                    _authState.value = AuthState.NotConfigured
                }
            } else {
                _authState.value = AuthState.NotConfigured
            }
        }
    }

    fun onServerUrlChanged(url: String) {
        _serverUrl.value = url
        _error.value = null
    }

    fun onUsernameChanged(username: String) {
        _username.value = username
        _error.value = null
    }

    fun onPasswordChanged(password: String) {
        _password.value = password
        _error.value = null
    }

    fun login() {
        val url = _serverUrl.value.trim()
        val username = _username.value.trim()
        val password = _password.value

        if (url.isBlank()) { _error.value = "请输入服务器地址"; return }
        if (username.isBlank()) { _error.value = "请输入账号"; return }
        if (password.isBlank()) { _error.value = "请输入密码"; return }

        _isLoggingIn.value = true
        _error.value = null

        viewModelScope.launch {
            var lastError: Throwable? = null
            for (candidate in candidateUrls(url)) {
                val result = authRepository.login(candidate, username, password, _rememberMe.value)
                if (result.isSuccess) {
                    _serverUrl.value = candidate
                    _isLoggingIn.value = false
                    _authState.value = AuthState.LoggedIn(result.getOrThrow())
                    stopConfigServer()
                    return@launch
                }
                lastError = result.exceptionOrNull()
                // 仅连接层失败（IOException）才换协议重试；服务器有真实响应（如账号密码错误）直接报错
                if (lastError !is IOException) break
            }
            _isLoggingIn.value = false
            _error.value = lastError?.message ?: "登录失败"
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _authState.value = AuthState.NotConfigured
        }
    }

    /** 清除服务器配置与登录状态（设置页「清除配置」），回到配置服务器页 */
    fun resetToConfig() {
        viewModelScope.launch {
            authRepository.clearAllAuth()
            _serverUrl.value = ""
            _username.value = ""
            _password.value = ""
            _rememberMe.value = false
            _authState.value = AuthState.NotConfigured
        }
    }

    fun setRememberMe(checked: Boolean) {
        _rememberMe.value = checked
    }

    override fun onCleared() {
        stopConfigServer()
    }

    companion object {
        private val CONFIG_PORTS = intArrayOf(18899, 18900, 18901, 18902)
    }
}
