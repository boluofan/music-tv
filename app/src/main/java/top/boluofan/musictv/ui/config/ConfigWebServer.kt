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
            <title>菠萝音乐 - 扫码配置</title>
            <style>
            body{font-family:-apple-system,sans-serif;background:#0f172a;color:#eee;margin:0;padding:24px}
            h2{color:#38bdf8;text-align:center}
            .desc{text-align:center;color:#94a3b8;font-size:14px;margin-bottom:24px}
            label{display:block;margin:16px 0 6px;font-size:14px;color:#94a3b8}
            input{width:100%;box-sizing:border-box;padding:12px;font-size:16px;
            border:1px solid #334155;border-radius:8px;background:#1e293b;color:#eee}
            .pw-wrap{position:relative}
            .pw-wrap input{padding-right:44px}
            .pw-toggle{position:absolute;right:6px;top:50%;transform:translateY(-50%);
            background:none;border:none;cursor:pointer;padding:8px;display:flex;
            align-items:center;justify-content:center;color:#94a3b8}
            .pw-toggle svg{width:20px;height:20px;display:block}
            button.submit{width:100%;margin-top:24px;padding:14px;font-size:16px;font-weight:bold;
            border:none;border-radius:8px;background:#38bdf8;color:#0f172a}
            </style></head><body>
            <h2>菠萝音乐</h2>
            <p class="desc">连接 lxserver 洛雪音乐服务</p>
            <form method="post" action="/submit">
              <label>服务地址</label>
              <input name="server" type="url" placeholder="http://192.168.x.x:9527" required>
              <label>用户名</label>
              <input name="username" type="text" placeholder="lxserver 管理控制台中配置的用户" required>
              <label>密码</label>
              <div class="pw-wrap">
                <input id="pw" name="password" type="password" placeholder="输入密码" required>
                <button type="button" class="pw-toggle" id="pwToggle" onclick="togglePw()" aria-label="显示密码">
                  <svg id="pwEye" viewBox="0 0 24 24" fill="currentColor"><path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/></svg>
                  <svg id="pwEyeOff" viewBox="0 0 24 24" fill="currentColor" style="display:none"><path d="M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z"/></svg>
                </button>
              </div>
              <button class="submit" type="submit">提交到电视</button>
            </form>
            <script>
            function togglePw(){
              var input=document.getElementById('pw');
              var show=input.type==='password';
              input.type=show?'text':'password';
              document.getElementById('pwEye').style.display=show?'none':'';
              document.getElementById('pwEyeOff').style.display=show?'':'none';
              document.getElementById('pwToggle').setAttribute('aria-label',show?'隐藏密码':'显示密码');
            }
            </script>
            </body></html>
        """.trimIndent()
    }
}
