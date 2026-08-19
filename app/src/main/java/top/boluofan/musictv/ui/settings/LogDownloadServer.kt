package top.boluofan.musictv.ui.settings

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

/** 局域网 Web 服务：手机扫码打开下载页，下载电视端导出的日志文件 */
class LogDownloadServer(port: Int, private val file: File) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.GET && session.uri == "/download") {
            if (!file.isFile) return text(Response.Status.NOT_FOUND, "文件不存在。")
            val response = newFixedLengthResponse(
                Response.Status.OK, "text/plain; charset=utf-8", FileInputStream(file), file.length()
            )
            response.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
            return response
        }
        return html(Response.Status.OK, PAGE.replace("{{name}}", file.name))
    }

    private fun html(status: Response.Status, content: String): Response =
        newFixedLengthResponse(status, "text/html; charset=utf-8", content)

    private fun text(status: Response.Status, content: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", content)

    companion object {
        private val PAGE = """
            <!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>菠萝音乐 - 日志下载</title>
            <style>
            body{font-family:-apple-system,sans-serif;background:#0f172a;color:#eee;margin:0;
            display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:90vh;padding:24px}
            h2{color:#38bdf8}
            .name{color:#94a3b8;font-size:14px;word-break:break-all;text-align:center;margin:12px 0 24px}
            a.btn{display:block;width:100%;box-sizing:border-box;padding:14px;font-size:16px;font-weight:bold;
            border:none;border-radius:8px;background:#38bdf8;color:#0f172a;text-align:center;text-decoration:none}
            </style></head><body>
            <h2>菠萝音乐</h2>
            <p class="name">{{name}}</p>
            <a class="btn" href="/download" download>下载日志文件</a>
            </body></html>
        """.trimIndent()
    }
}
