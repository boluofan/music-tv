package top.boluofan.musictv.data.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object ApiClient {
    private var baseUrl: String = ""
    private var musicApi: LxMusicApi? = null
    private var userApi: LxUserApi? = null
    private var dataApi: LxDataApi? = null

    val authInterceptor = AuthInterceptor()

    fun initialize(url: String) {
        if (url == baseUrl && musicApi != null) return
        baseUrl = url.trimEnd('/')

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = TlsCompat.apply(OkHttpClient.Builder())
            .dns(IPv4FirstDns)
            .addInterceptor(LoginTimeoutInterceptor())
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        fun retrofit(apiUrl: String, gson: Gson): Retrofit = Retrofit.Builder()
            .baseUrl(apiUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        // 搜索响应兼容工厂只挂在 music 接口上，且工厂内部又只认 SearchEnvelope 类型，
        // 因此其它接口的 List 返回值不受影响（后端报错不会被静默解析成空列表）。
        val musicGson = GsonBuilder()
            .registerTypeAdapterFactory(SearchEnvelopeAdapterFactory())
            .create()
        val plainGson = Gson()

        musicApi = retrofit("$baseUrl/api/music/", musicGson).create(LxMusicApi::class.java)
        userApi = retrofit("$baseUrl/api/user/", plainGson).create(LxUserApi::class.java)
        dataApi = retrofit("$baseUrl/api/data/", plainGson).create(LxDataApi::class.java)
    }

    fun getMusicApi(): LxMusicApi = musicApi ?: error("ApiClient 未初始化")

    fun getUserApi(): LxUserApi = userApi ?: error("ApiClient 未初始化")

    fun getDataApi(): LxDataApi = dataApi ?: error("ApiClient 未初始化")
}

class AuthInterceptor : Interceptor {
    @Volatile var username: String? = null
    @Volatile var token: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
        token?.let { builder.addHeader("x-user-token", it) }
        username?.let { builder.addHeader("x-user-name", it) }
        return chain.proceed(builder.build())
    }
}

/**
 * IPv4 优先的 DNS 解析。
 *
 * 部分域名（真机上的 lx.boluofan.top）同时有 A / AAAA 记录，但家庭宽带或电视的
 * IPv6 出口并不通。OkHttp 按 DNS 返回顺序先连 IPv6，直到耗尽整个 connectTimeout
 * 才回退 IPv4 成功 —— 旧版实测 30.1s、放宽后 20.3s，用户看到的就是「登录要等很久」。
 * 这里只调整顺序、不丢弃地址，纯 IPv6 服务器仍能正常连接。
 */
internal object IPv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        val (ipv6, ipv4) = addresses.partition { it is Inet6Address }
        return ipv4 + ipv6
    }
}

/**
 * 登录接口单独放宽读超时。
 *
 * 真机实测：部分反向代理 / 公网 HTTPS 环境下 `/api/user/login` 耗时约 30.1s，
 * 正好越过全局 30s 读超时，用户看到的是「一直卡在连接并登录中」。
 * 其它接口保持 30s 不变，避免慢请求拖久整体体验。
 */
private class LoginTimeoutInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.endsWith("/api/user/login")) {
            return chain.proceed(request)
        }
        return chain
            .withConnectTimeout(20, TimeUnit.SECONDS)
            .withReadTimeout(60, TimeUnit.SECONDS)
            .withWriteTimeout(30, TimeUnit.SECONDS)
            .proceed(request)
    }
}
