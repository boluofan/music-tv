package top.boluofan.musictv.data.api

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

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
 * - JSON 为对象 → 按字段手工解析（新版后端）
 * - JSON 为数组 → 包一层 `{"list": [...]}` 再解析（旧版后端）
 * - JSON 为不带 list 的错误对象 → 抛 [IOException]，避免把服务端故障静默吞成空列表
 *
 * 刻意不使用 `gson.getDelegateAdapter()` 的反射适配器：release 包开启 R8
 * （proguard-android-optimize.txt），未被 keep 的类会被改名，Kotlin 全默认值 data class
 * 生成的合成无参构造器也会被优化掉，Gson 反射构造时抛
 * "Unable to invoke no-args constructor ... Registering an InstanceCreator"。
 * 这里直接调用 Kotlin 构造器，完全避开类名/字段名/构造器混淆；泛型元素仍交给 Gson 解析
 * （data.model 下的类有专门的 keep 规则）。
 *
 * 该工厂也只挂在 music 接口的 Retrofit 实例上，其它 `List` 返回值不受影响。
 */
class SearchEnvelopeAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType != SearchEnvelope::class.java) return null
        val elementType: Type = (type.type as? ParameterizedType)
            ?.actualTypeArguments?.firstOrNull() ?: Any::class.java
        @Suppress("UNCHECKED_CAST")
        val listAdapter = gson.getAdapter(
            TypeToken.getParameterized(List::class.java, elementType)
        ) as TypeAdapter<List<Any?>>
        val jsonElementAdapter = gson.getAdapter(JsonElement::class.java)

        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T) {
                if (value == null) {
                    out.nullValue()
                    return
                }
                val envelope = value as SearchEnvelope<*>
                out.beginObject()
                out.name("list")
                @Suppress("UNCHECKED_CAST")
                listAdapter.write(out, envelope.items as List<Any?>)
                envelope.total?.let { out.name("total").value(it.toLong()) }
                envelope.allPage?.let { out.name("allPage").value(it.toLong()) }
                envelope.pageSize?.let { out.name("pageSize").value(it.toLong()) }
                envelope.fetchedPages?.let { out.name("fetchedPages").value(it.toLong()) }
                envelope.source?.let { out.name("source").value(it) }
                envelope.hasMore?.let { out.name("hasMore").value(it) }
                envelope.stopReason?.let { out.name("stopReason").value(it) }
                out.endObject()
            }

            override fun read(reader: JsonReader): T? {
                val element = jsonElementAdapter.read(reader)
                val obj: JsonObject = when {
                    element.isJsonArray -> JsonObject().apply { add("list", element) }
                    element.isJsonObject -> element.asJsonObject
                    else -> throw IOException("搜索接口返回了异常数据")
                }
                if (!obj.has("list")) {
                    val reason = listOf("message", "error")
                        .mapNotNull { key -> obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString }
                        .firstOrNull()
                    throw IOException(reason ?: "搜索接口返回了异常数据")
                }
                val listJson = obj.get("list")
                val items = if (listJson != null && listJson.isJsonArray) {
                    listAdapter.fromJsonTree(listJson).orEmpty()
                } else {
                    emptyList()
                }
                @Suppress("UNCHECKED_CAST")
                return SearchEnvelope(
                    list = items,
                    total = obj.intOrNull("total"),
                    allPage = obj.intOrNull("allPage"),
                    pageSize = obj.intOrNull("pageSize"),
                    fetchedPages = obj.intOrNull("fetchedPages"),
                    source = obj.stringOrNull("source"),
                    hasMore = obj.boolOrNull("hasMore"),
                    stopReason = obj.stringOrNull("stopReason")
                ) as T
            }
        }
    }

    private fun JsonObject.intOrNull(name: String): Int? = runCatching {
        get(name)?.takeIf { it.isJsonPrimitive }?.asInt
    }.getOrNull()

    private fun JsonObject.stringOrNull(name: String): String? = runCatching {
        get(name)?.takeIf { it.isJsonPrimitive }?.asString
    }.getOrNull()

    private fun JsonObject.boolOrNull(name: String): Boolean? = runCatching {
        get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean
    }.getOrNull()
}
