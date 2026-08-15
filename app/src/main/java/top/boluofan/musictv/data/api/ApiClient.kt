package top.boluofan.musictv.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        fun retrofit(apiUrl: String): Retrofit = Retrofit.Builder()
            .baseUrl(apiUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        musicApi = retrofit("$baseUrl/api/music/").create(LxMusicApi::class.java)
        userApi = retrofit("$baseUrl/api/user/").create(LxUserApi::class.java)
        dataApi = retrofit("$baseUrl/api/data/").create(LxDataApi::class.java)
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
