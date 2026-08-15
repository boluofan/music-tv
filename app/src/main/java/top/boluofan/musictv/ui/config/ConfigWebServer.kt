package top.boluofan.musictv.ui.config

import fi.iki.elonen.NanoHTTPD
import java.util.HashMap

/**
 * 局域网 Web 服务：手机扫码打开配置页，填写服务器地址/账号/密码
 * 提交（POST /submit）后回传电视端触发登录。
 */
class ConfigWebServer(
    port: Int,
    private val onConfig: (server: String, username: String, password: String) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.POST && session.uri == "/submit") {
            session.parseBody(HashMap())
            val params = session.parameters
            val server = params["server"]?.firstOrNull()?.trim().orEmpty()
            val username = params["username"]?.firstOrNull()?.trim().orEmpty()
            val password = params["password"]?.firstOrNull().orEmpty()
            return if (server.isBlank() || username.isBlank() || password.isBlank()) {
                html(Response.Status.BAD_REQUEST, resultPage("配置失败", "请完整填写服务器地址、账号和密码。"))
            } else {
                onConfig(server, username, password)
                html(Response.Status.OK, resultPage("已提交", "电视端正在登录，请查看电视屏幕。"))
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
        fun localIpAddress(): String? =
            java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<java.net.Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress

        private val PAGE = """
            <!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>菠萝音乐 TV - 扫码配置</title>
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
            <p class="desc">连接 lxserver 洛雪音乐服务</p>
            <form method="post" action="/submit">
              <label>服务地址</label>
              <input name="server" type="url" placeholder="http://192.168.x.x:9527" required>
              <label>用户名</label>
              <input name="username" type="text" placeholder="lxserver 管理控制台中配置的用户" required>
              <label>密码</label>
              <input name="password" type="password" placeholder="输入密码" required>
              <button class="submit" type="submit">提交到电视</button>
            </form>
            </body></html>
        """.trimIndent()
    }
}
