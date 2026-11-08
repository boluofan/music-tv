package top.boluofan.musictv.data.api

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException

/**
 * `/api/music/search` 的响应包装，用于同时兼容两代 lxserver：
 *
 * - 新版（>= 2.1.3）四种 type 都返回对象：
 *   `{ list, total, allPage, pageSize, fetchedPages, source, hasMore, stopReason }`
 * - 旧版（<= 2.1.2，线上仍有部署）返回裸数组 `[...]`
 *
 * [SearchEnvelopeAdapterFactory] 会把裸数组包装成 `{"list": [...]}`，
 * 因此调用方只需要读 [items]，无需关心服务端版本。
 */
data class SearchEnvelope<T>(
    @SerializedName("list") val list: List<T>? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("allPage") val allPage: Int? = null,
    @SerializedName("pageSize") val pageSize: Int? = null,
    @SerializedName("fetchedPages") val fetchedPages: Int? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("hasMore") val hasMore: Boolean? = null,
    /**
     * 新版歌曲搜索专有：显式传了 limit 时固定为 "single-page"，
     * 不带 limit 走服务端聚合时为 empty-page / repeat-page / page-cap / time-budget 等。
     */
    @SerializedName("stopReason") val stopReason: String? = null
) {
    val items: List<T> get() = list.orEmpty()

    /** 是否来自新版后端的对象响应（旧版数组被包装后所有元数据都是 null） */
    val isEnvelopeResponse: Boolean
        get() = stopReason != null || total != null || fetchedPages != null || allPage != null
}

/**
 * 仅对 [SearchEnvelope] 生效的 Gson 工厂：
 *
 * - JSON 为对象 → 交给 Gson 默认反射解析（新版后端）
 * - JSON 为数组 → 包一层 `{"list": [...]}` 再解析（旧版后端）
 * - JSON 为不带 list 的错误对象 → 抛 [IOException]，避免把服务端故障静默吞成空列表
 *
 * 刻意不注册为全局 List 适配器：`tipSearch`、`getLibraryArtists`、`getArtistSongs`
 * 等接口的 `List` 返回值仍走 Gson 默认行为，后端报错不会被掩盖成空结果。
 * 该工厂也只挂在 music 接口的 Retrofit 实例上，进一步缩小影响面。
 */
class SearchEnvelopeAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType != SearchEnvelope::class.java) return null
        val delegate = gson.getDelegateAdapter(this, type)
        val jsonElementAdapter = gson.getAdapter(JsonElement::class.java)

        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T) {
                delegate.write(out, value)
            }

            override fun read(reader: JsonReader): T? {
                return when (reader.peek()) {
                    JsonToken.BEGIN_ARRAY -> {
                        val array = jsonElementAdapter.read(reader)
                        val wrapped = JsonObject().apply { add("list", array) }
                        delegate.fromJsonTree(wrapped)
                    }
                    JsonToken.BEGIN_OBJECT -> {
                        val obj = jsonElementAdapter.read(reader) as JsonObject
                        if (!obj.has("list")) {
                            val reason = listOf("message", "error")
                                .mapNotNull { key -> obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString }
                                .firstOrNull()
                            throw IOException(reason ?: "搜索接口返回了异常数据")
                        }
                        delegate.fromJsonTree(obj)
                    }
                    else -> delegate.read(reader)
                }
            }
        }
    }
}
