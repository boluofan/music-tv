package top.boluofan.musictv.data.api

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.internal.bind.TypeAdapters
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/**
 * 兼容 lxserver 搜索接口响应格式变化：
 * - v2.1.2 及以前：根节点为数组 [...]
 * - v2.1.3+：根节点为对象 {"list":[...],"total":..,"hasMore":..}
 *
 * 当目标类型是 List 而实际 JSON 是对象时，自动取其中的 "list" 字段作为列表。
 */
class ListCompatAdapterFactory : TypeAdapterFactory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (!List::class.java.isAssignableFrom(type.rawType) && type.rawType != java.util.List::class.java) return null
        val delegate = gson.getDelegateAdapter(this, type)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T) = delegate.write(out, value)

            override fun read(reader: JsonReader): T {
                val element: JsonElement = TypeAdapters.JSON_ELEMENT.read(reader)
                val normalized: JsonElement = when {
                    element.isJsonObject -> {
                        val obj: JsonObject = element.asJsonObject
                        val inner = obj.get("list")
                        if (inner != null && inner.isJsonArray) inner else JsonArray()
                    }
                    else -> element
                }
                return delegate.fromJsonTree(normalized)
            }
        }
    }
}
