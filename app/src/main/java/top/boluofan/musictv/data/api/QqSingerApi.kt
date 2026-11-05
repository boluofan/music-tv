package top.boluofan.musictv.data.api

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * QQ 音乐热门歌手（仅用于「歌手」搜索页的热门推荐）。
 *
 * 注意：这里必须使用独立的 OkHttpClient，不能复用 ApiClient 里带 AuthInterceptor 的客户端，
 * 否则会把 lxserver 的登录令牌发给第三方域名。
 */
object QqSingerApi {
    private const val URL =
        "https://c.y.qq.com/v8/fcg-bin/v8.fcg?channel=singer&page=list&key=all_all_all" +
            "&pagesize=30&pagenum=1&g_tk=5381&loginUin=0&hostUin=0&format=json" +
            "&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq&needNewCode=0"

    // 接口实际会返回 100 位歌手（pagesize 参数不生效），这里只取前 30 位
    private const val MAX_COUNT = 30

    private val BRACKET = Regex("^(.*?)\\s*[（(](.+?)[）)]\\s*$")
    private val HANGUL = Regex("[\\uAC00-\\uD7AF]")

    /**
     * QQ 返回的名称常带别名，如「BIGBANG (빅뱅)」「Alan Walker (艾伦·沃克)」，
     * 直接拿去搜索效果差，这里去掉括号部分；主名是韩文时改用括号里的名字。
     */
    private fun cleanName(raw: String): String {
        val name = raw.trim()
        val m = BRACKET.matchEntire(name) ?: return name
        val main = m.groupValues[1].trim()
        val alias = m.groupValues[2].trim()
        return if (main.isEmpty() || HANGUL.containsMatchIn(main)) alias else main
    }

    private val client: OkHttpClient by lazy {
        TlsCompat.apply(OkHttpClient.Builder())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** 返回热门歌手名称列表；失败时抛异常，由调用方决定如何兜底。 */
    suspend fun hotSingers(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(URL)
            .header("Referer", "https://y.qq.com/")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val list = JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("data")
                ?.getAsJsonArray("list")
                ?: error("响应中没有 data.list")
            list.mapNotNull { item ->
                val obj = item.asJsonObject
                (obj.get("Fsinger_name") ?: obj.get("singer_name"))
                    ?.takeIf { !it.isJsonNull }?.asString?.let(::cleanName)
            }.filter { it.isNotEmpty() }.distinct().take(MAX_COUNT)
        }
    }
}
