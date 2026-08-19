package top.boluofan.musictv.data.api

object UrlHelper {
    private var baseUrl: String = ""

    fun initialize(url: String) {
        baseUrl = url.trimEnd('/')
    }

    /** 将相对路径（如 /api/music/xxx/pic）解析为可加载的绝对 URL */
    fun resolve(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        return if (url.startsWith("/")) "$baseUrl$url" else "$baseUrl/$url"
    }
}
