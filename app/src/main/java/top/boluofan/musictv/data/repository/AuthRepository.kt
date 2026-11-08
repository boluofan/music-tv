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

/** 登录失败：携带 HTTP 状态码，便于 UI 给出可操作的中文提示 */
class LoginFailureException(
    message: String,
    val httpCode: Int? = null
) : IllegalStateException(message)

@Singleton
class AuthRepository @Inject constructor(
    private val dataStore: PreferencesDataStore
) {
    suspend fun login(serverUrl: String, username: String, password: String, rememberMe: Boolean = false): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.initialize(serverUrl)
                UrlHelper.initialize(serverUrl)
                // 手动重新登录时不要带上一次已失效的 token
                ApiClient.authInterceptor.token = null
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

    /**
     * 启动自动登录：恢复会话并校验 token。
     *
     * 服务端 token 有效期约 7 天，过期后原先会直接掉回「未配置」让用户重新扫码输入。
     * 若勾选了「记住登录」且保存了密码，这里静默重登一次换发新 token，用户无感。
     */
    suspend fun tryAutoLogin(): Boolean = withContext(Dispatchers.IO) {
        val snapshot = dataStore.authSnapshot()
        val url = snapshot.serverUrl
        val token = snapshot.token
        if (url.isNullOrEmpty() || token.isNullOrEmpty()) return@withContext false

        ApiClient.initialize(url)
        UrlHelper.initialize(url)
        ApiClient.authInterceptor.username = snapshot.username
        ApiClient.authInterceptor.token = token

        val valid = runCatching { ApiClient.getUserApi().verifyAuth().valid }.getOrDefault(false)
        if (valid) return@withContext true

        val name = snapshot.username
        val password = snapshot.password
        if (!snapshot.rememberMe || name.isNullOrBlank() || password.isNullOrEmpty()) {
            return@withContext false
        }

        ApiClient.authInterceptor.token = null
        runCatching {
            val response = ApiClient.getUserApi().login(
                mapOf("username" to name, "password" to password)
            )
            if (!response.success || response.token.isNullOrEmpty()) {
                false
            } else {
                applyAuth(url, response.username ?: name, response.token, true, password)
                true
            }
        }.getOrElse {
            // 重登失败时恢复旧 token，不影响用户下次启动继续尝试
            ApiClient.authInterceptor.token = token
            false
        }
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
     * 真实原因在响应体 JSON（如 {"success":false,"message":"Invalid credentials"}）里。
     * 这里解析出来并换成可操作的中文提示；IOException 保持原样，让 ViewModel 继续换协议重试。
     */
    private fun <T> Result<T>.mapLoginFailure(): Result<T> {
        val e = exceptionOrNull() ?: return this
        if (e !is HttpException) return this
        val body = e.response()?.errorBody()?.string().orEmpty()
        val parsed = runCatching { Gson().fromJson(body, LoginErrorBody::class.java) }.getOrNull()
        val raw = parsed?.message ?: parsed?.error ?: e.message() ?: "HTTP ${e.code()}"
        val friendly = when (e.code()) {
            // 后端 /api/user/login 只校验 users[].password；用户常把后台管理员密码
            // 或 Web 播放器密码填进来（issue #20），这里直接点明该用哪一个。
            401 -> "账号或密码错误。请填写服务端「用户管理」里为该账号设置的用户同步密码，" +
                "不是后台管理员密码或 Web 播放器密码"
            403 -> "该账号已被禁用，请联系服务器管理员启用"
            400 -> "登录请求格式有误，请检查账号和密码：$raw"
            else -> "服务器返回 ${e.code()}：$raw"
        }
        return Result.failure(LoginFailureException(friendly, e.code()))
    }

    private data class LoginErrorBody(
        val message: String? = null,
        val error: String? = null
    )
}
