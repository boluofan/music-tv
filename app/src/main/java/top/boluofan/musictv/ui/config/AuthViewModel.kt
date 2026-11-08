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
import top.boluofan.musictv.data.repository.LoginFailureException
import top.boluofan.musictv.data.storage.PreferencesDataStore
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.inject.Inject
import javax.net.ssl.SSLHandshakeException

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

    /**
     * 生成候选服务器地址：优先用户填写的协议，连接层失败时再回退另一种协议。
     * 以前带 http(s):// 的地址只有一个候选，反代或证书配置不对时完全没有回退机会
     * （真机实测登录耗时贴近 30s 超时，用户看到的只有「一直卡住」）。
     */
    private fun candidateUrls(url: String): List<String> {
        val normalized = url.trim().trimEnd('/')
        return when {
            normalized.startsWith("https://") ->
                listOf(normalized, "http://" + normalized.removePrefix("https://"))
            normalized.startsWith("http://") ->
                listOf(normalized, "https://" + normalized.removePrefix("http://"))
            else -> listOf("https://$normalized", "http://$normalized")
        }
    }

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
                    // token 过期且自动重登未成功（未记住密码 / 密码已改）时给出可操作提示，
                    // 避免用户只看到「又回到配置页」却不知道发生了什么
                    _authState.value = AuthState.NotConfigured
                    _error.value = "登录状态已失效，请重新输入账号密码登录"
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
            var attempts = 0
            for (candidate in candidateUrls(url)) {
                attempts++
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
            _error.value = buildString {
                append(friendlyLoginError(lastError))
                if (attempts > 1) append("（已自动尝试 HTTP 与 HTTPS 两种协议）")
            }
        }
    }

    /** 把底层异常翻译成用户能据此操作的提示，而不是 "timeout" / "HTTP 401" */
    private fun friendlyLoginError(e: Throwable?): String {
        if (e == null) return "登录失败"
        return when {
            e is LoginFailureException -> e.message ?: "登录失败"
            e is SocketTimeoutException ->
                "连接服务器超时（最长等待 60 秒）。请检查网络、服务器是否在线，以及地址端口是否正确"
            e is SSLHandshakeException || e is CertificateException ->
                "HTTPS 证书校验失败。可改用 http:// 地址，或在服务器上配置有效证书"
            e is UnknownHostException -> "找不到服务器主机，请检查地址是否填写正确"
            e is ConnectException -> "无法连接服务器，请检查地址、端口与服务是否已启动"
            e is IOException -> "网络连接失败：${e.message ?: "未知原因"}。请检查服务器地址与网络"
            else -> e.message ?: "登录失败"
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
