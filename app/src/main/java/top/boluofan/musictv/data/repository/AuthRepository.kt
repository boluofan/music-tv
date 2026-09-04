package top.boluofan.musictv.data.repository

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import top.boluofan.musictv.data.api.ApiClient
import top.boluofan.musictv.data.api.UrlHelper
import top.boluofan.musictv.data.storage.PreferencesDataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val dataStore: PreferencesDataStore
) {
    suspend fun login(serverUrl: String, username: String, password: String, rememberMe: Boolean = false): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.initialize(serverUrl)
                UrlHelper.initialize(serverUrl)
                val response = ApiClient.getUserApi().login(
                    mapOf("username" to username, "password" to password)
                )
                if (!response.success || response.token.isNullOrEmpty()) {
                    throw IllegalStateException(response.message ?: "登录失败")
                }
                applyAuth(serverUrl, response.username ?: username, response.token, rememberMe, if (rememberMe) password else null)
                response.username ?: username
            }.mapLoginFailure()
        }

    /** 启动自动登录：从 DataStore 恢复会话并校验 token */
    suspend fun tryAutoLogin(): Boolean = withContext(Dispatchers.IO) {
        val snapshot = dataStore.authSnapshot()
        val url = snapshot.serverUrl
        val token = snapshot.token
        if (url.isNullOrEmpty() || token.isNullOrEmpty()) return@withContext false
        runCatching {
            ApiClient.initialize(url)
            UrlHelper.initialize(url)
            ApiClient.authInterceptor.username = snapshot.username
            ApiClient.authInterceptor.token = token
            ApiClient.getUserApi().verifyAuth().valid
        }.getOrDefault(false)
    }

    suspend fun logout() {
        runCatching { ApiClient.getUserApi().logout() }
        dataStore.clearAuth()
        ApiClient.authInterceptor.username = null
        ApiClient.authInterceptor.token = null
    }

    private suspend fun applyAuth(serverUrl: String, username: String, token: String, rememberMe: Boolean = false, password: String? = null) {
        ApiClient.authInterceptor.username = username
        ApiClient.authInterceptor.token = token
        dataStore.setAuth(serverUrl, username, token, rememberMe, password)
    }

    /** 清除服务器配置与登录状态（设置页「清除配置」） */
    suspend fun clearAllAuth() {
        dataStore.clearAllAuth()
        ApiClient.authInterceptor.username = null
        ApiClient.authInterceptor.token = null
    }

    /**
     * 服务器返回 401 时 Retrofit 抛出的 HttpException.message 只是 "HTTP 401"，
     * 真实原因在响应体 JSON（如 {"success":false,"message":"Invalid credentials"}）里，这里解析出来替换。
     */
    private fun <T> Result<T>.mapLoginFailure(): Result<T> {
        val e = exceptionOrNull() ?: return this
        // 仅转换 HttpException；IOException 保持原样，让 ViewModel 继续换协议重试
        if (e !is HttpException) return this
        val body = e.response()?.errorBody()?.string().orEmpty()
        val parsed = runCatching { Gson().fromJson(body, LoginErrorBody::class.java) }.getOrNull()
        val message = parsed?.message ?: parsed?.error ?: e.message() ?: "HTTP ${e.code()}"
        return Result.failure(IllegalStateException(message))
    }

    private data class LoginErrorBody(
        val message: String? = null,
        val error: String? = null
    )
}
