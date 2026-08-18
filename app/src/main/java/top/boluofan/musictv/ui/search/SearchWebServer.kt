package top.boluofan.musictv.ui.search

import fi.iki.elonen.NanoHTTPD
import java.util.HashMap

/**
 * 局域网 Web 服务：手机扫码打开搜索页，输入关键词后提交（POST /search），
 * 回传电视端触发搜索，可反复提交。
 */
class SearchWebServer(
    port: Int,
    private val onSearch: (query: String) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.POST && session.uri == "/search") {
            session.parseBody(HashMap())
            val query = session.parameters["query"]?.firstOrNull()?.trim().orEmpty()
            return if (query.isBlank()) {
                html(Response.Status.BAD_REQUEST, resultPage("搜索失败", "请输入关键词。"))
            } else {
                onSearch(query)
                html(Response.Status.OK, resultPage("已提交", "电视端正在搜索：$query"))
            }
        }
        return html(Response.Status.OK, PAGE)
    }

    private fun html(status: Response.Status, content: String): Response =
        newFixedLengthResponse(status, "text/html; charset=utf-8", content)

    private fun resultPage(title: String, message: String) = """
        <!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>$title</title>
        <style>body{font-family:sans-serif;background:#0f172a;color:#eee;
        display:flex;flex-direction:column;align-items:center;justify-content:center;
        min-height:90vh;margin:0;padding:16px}h2{color:#38bdf8}</style></head>
        <body><h2>$title</h2><p>$message</p></body></html>
    """.trimIndent()

    companion object {
        private val PAGE = """
            <!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>菠萝音乐 TV - 扫码搜索</title>
            <style>
            body{font-family:-apple-system,sans-serif;background:#0f172a;color:#eee;margin:0;padding:24px}
            h2{color:#38bdf8;text-align:center}
            .desc{text-align:center;color:#94a3b8;font-size:14px;margin-bottom:24px}
            label{display:block;margin:16px 0 6px;font-size:14px;color:#94a3b8}
            input{width:100%;box-sizing:border-box;padding:12px;font-size:16px;
            border:1px solid #334155;border-radius:8px;background:#1e293b;color:#eee}
            button.submit{width:100%;margin-top:24px;padding:14px;font-size:16px;font-weight:bold;
            border:none;border-radius:8px;background:#38bdf8;color:#0f172a}
            </style></head><body>
            <h2>菠萝音乐 TV</h2>
            <p class="desc">手机输入关键词，电视端同步搜索</p>
            <form method="post" action="/search">
              <label>搜索关键词</label>
              <input name="query" type="text" placeholder="输入歌曲 / 歌手 / 专辑" required>
              <button class="submit" type="submit">发送到电视搜索</button>
            </form>
            </body></html>
        """.trimIndent()
    }
}
