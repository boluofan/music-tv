package top.boluofan.musictv.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * QQ 音乐热门歌手，仅用于「歌手」搜索页的热门推荐词（参考 PR #21）。
 *
 * 必须使用独立的 OkHttpClient：ApiClient 的客户端挂了 AuthInterceptor，
 * 复用会把 lxserver 的用户 token / 用户名发送给第三方域名。
 * 请求失败时抛异常，由 SearchViewModel 回退到 lxserver 自带的热搜词。
 */
object QqSingerApi {
    private const val URL =
        "https://c.y.qq.com/v8/fcg-bin/v8.fcg?channel=singer&page=list&key=all_all_all" +
            "&pagesize=30&pagenum=1&g_tk=5381&loginUin=0&hostUin=0&format=json" +
            "&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq&needNewCode=0"

    // 实测接口固定返回 100 位（pagesize 参数不生效），电视上只展示前 45 位
    private const val MAX_COUNT = 45

    private val BRACKET = Regex("^(.*?)\\s*[（(](.+?)[）)]\\s*$")
    private val HANGUL = Regex("[\\uAC00-\\uD7AF]")

    /**
     * QQ 返回的名称常带别名，如「BIGBANG (빅뱅)」「Alan Walker (艾伦·沃克)」，
     * 整串拿去搜索命中率很差，这里去掉括号部分；主名是韩文时改用括号里的名字。
     */
    private fun cleanName(raw: String): String {
        val name = raw.trim()
        val match = BRACKET.matchEntire(name) ?: return name
        val main = match.groupValues[1].trim()
        val alias = match.groupValues[2].trim()
        return if (main.isEmpty() || HANGUL.containsMatchIn(main)) alias else main
    }

    private val client: OkHttpClient by lazy {
        TlsCompat.apply(OkHttpClient.Builder())
            .dns(IPv4FirstDns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** 返回热门歌手名称列表；失败抛异常，由调用方决定如何兜底。 */
    suspend fun hotSingers(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(URL)
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("QQ 音乐热门歌手 HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val root = runCatching { Gson().fromJson(body, JsonObject::class.java) }.getOrNull()
                ?: error("QQ 音乐热门歌手响应不是合法 JSON")
            val list = root.getAsJsonObject("data")?.getAsJsonArray("list")
                ?: error("QQ 音乐热门歌手响应中没有 data.list")
            val names = list.mapNotNull { item ->
                val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val raw = (obj.get("Fsinger_name") ?: obj.get("singer_name"))
                    ?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
                cleanName(raw)
            }.filter { it.isNotEmpty() }.distinct().take(MAX_COUNT)
            names.ifEmpty { error("QQ 音乐热门歌手列表为空") }
        }
    }
}
