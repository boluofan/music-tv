package top.boluofan.musictv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LoginWebServer extends NanoHTTPD {
    private static final String TAG = "LoginWebServer";
    private Context context;
    private OnLoginDataReceivedListener listener;
    private String initialUrl;
    private String initialUsername;
    private String initialPassword;
    private String initialToken;
    private String initialApiType;

    public interface OnLoginDataReceivedListener {
        void onDataReceived(String url, String username, String password, String token, String apiType);
    }

    public LoginWebServer(Context context, int port, OnLoginDataReceivedListener listener,
                          String initialUrl, String initialUsername, String initialPassword, String initialToken, String initialApiType) {
        super(port);
        this.context = context;
        this.listener = listener;
        this.initialUrl = initialUrl != null ? initialUrl : "";
        this.initialUsername = initialUsername != null ? initialUsername : "";
        this.initialPassword = initialPassword != null ? initialPassword : "";
        this.initialToken = initialToken != null ? initialToken : "";
        this.initialApiType = initialApiType != null ? initialApiType : "music";
    }

    public LoginWebServer(Context context, int port, OnLoginDataReceivedListener listener) {
        this(context, port, listener, null, null, null, null, null);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        if (Method.GET.equals(method) && "/".equals(uri)) {
            return newFixedLengthResponse(getHtml());
        }

        if (Method.POST.equals(method) && "/login".equals(uri)) {
            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                Map<String, String> params = session.getParms();

                String url = params.get("url");
                String username = params.get("username");
                String password = params.get("password");
                String token = params.get("token");
                String apiType = params.get("apiType");

                if (listener != null) {
                    listener.onDataReceived(url, username, password, token, apiType);
                }

                return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "SUCCESS");
            } catch (IOException | NanoHTTPD.ResponseException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "ERROR: " + e.getMessage());
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private String getHtml() {
        String urlValue = escapeHtml(initialUrl);
        String usernameValue = escapeHtml(initialUsername);
        String passwordValue = escapeHtml(initialPassword);
        String tokenValue = escapeHtml(initialToken);
        String apiTypeMusic = "music".equals(initialApiType) ? "checked" : "";
        String apiTypeTv = "tv".equals(initialApiType) ? "checked" : "";

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>菠萝音乐 - 扫码登录</title>\n" +
                "    <style>\n" +
                "        body { font-family: -apple-system, sans-serif; background: #0f172a; color: white; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }\n" +
                "        .card { background: #1e293b; padding: 24px; border-radius: 16px; width: 100%; max-width: 400px; box-shadow: 0 10px 25px rgba(0,0,0,0.3); }\n" +
                "        h2 { margin-top: 0; text-align: center; color: #38bdf8; }\n" +
                "        .desc { text-align: center; color: #94a3b8; font-size: 14px; margin-bottom: 24px; }\n" +
                "        .field { margin-bottom: 16px; }\n" +
                "        label { display: block; margin-bottom: 6px; color: #94a3b8; font-size: 13px; }\n" +
                "        input { width: 100%; padding: 12px; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: white; box-sizing: border-box; font-size: 16px; }\n" +
                "        button { width: 100%; padding: 14px; border-radius: 8px; border: none; background: #38bdf8; color: #0f172a; font-weight: bold; font-size: 16px; cursor: pointer; margin-top: 8px; }\n" +
                "        #status { text-align: center; margin-top: 16px; font-size: 14px; }\n" +
                "        .radio-group { display: flex; gap: 12px; margin-bottom: 16px; }\n" +
                "        .radio-item { flex: 1; }\n" +
                "        .radio-item input { width: auto; padding: 10px; }\n" +
                "        .radio-item label { display: inline; margin-left: 8px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"card\">\n" +
                "        <h2>菠萝音乐 TV - 快速配置</h2>\n" +
                "        <p class=\"desc\" id=\"desc\">选择服务器类型并输入信息推送到电视</p>\n" +
                "        <div class=\"field\">\n" +
                "            <label>API 类型</label>\n" +
                "            <div class=\"radio-group\">\n" +
                "                <div class=\"radio-item\">\n" +
                "                    <input type=\"radio\" id=\"apiLxserver\" name=\"apiType\" value=\"music\" " + apiTypeMusic + " onchange=\"toggleFields()\">\n" +
                "                    <label for=\"apiLxserver\">LXServer</label>\n" +
                "                </div>\n" +
                "                <div class=\"radio-item\">\n" +
                "                    <input type=\"radio\" id=\"apiMiMusic\" name=\"apiType\" value=\"tv\" " + apiTypeTv + " onchange=\"toggleFields()\">\n" +
                "                    <label for=\"apiMiMusic\">MiMusic</label>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div class=\"field\">\n" +
                "            <label>服务地址</label>\n" +
                "            <input type=\"url\" id=\"url\" placeholder=\"http://192.168.x.x:58090\" value=\"" + urlValue + "\" required>\n" +
                "        </div>\n" +
                "        <div class=\"field\">\n" +
                "            <label>用户名</label>\n" +
                "            <input type=\"text\" id=\"username\" placeholder=\"Username\" value=\"" + usernameValue + "\">\n" +
                "        </div>\n" +
                "        <div class=\"field\">\n" +
                "            <label>密码</label>\n" +
                "            <input type=\"password\" id=\"password\" placeholder=\"Password\" value=\"" + passwordValue + "\">\n" +
                "        </div>\n" +
                "        <div class=\"field\" id=\"tokenField\">\n" +
                "            <label>Token (可选)</label>\n" +
                "            <input type=\"text\" id=\"token\" placeholder=\"User Token\" value=\"" + tokenValue + "\">\n" +
                "        </div>\n" +
                "        <button onclick=\"submitLogin()\" id=\"btn\">推送到电视</button>\n" +
                "        <div id=\"status\"></div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        function toggleFields() {\n" +
                "            const isMiMusic = document.getElementById('apiMiMusic').checked;\n" +
                "            const desc = document.getElementById('desc');\n" +
                "            const tokenField = document.getElementById('tokenField');\n" +
                "            const urlInput = document.getElementById('url');\n" +
                "            \n" +
                "            if (isMiMusic) {\n" +
                "                desc.innerText = '连接 MiMusic 音源服务';\n" +
                "                tokenField.style.display = 'none';\n" +
                "                urlInput.placeholder = 'http://192.168.x.x:58091/api/v1';\n" +
                "            } else {\n" +
                "                desc.innerText = '连接 lxserver 洛雪音乐服务';\n" +
                "                tokenField.style.display = 'block';\n" +
                "                urlInput.placeholder = 'http://192.168.x.x:58090';\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        toggleFields();\n" +
                "        \n" +
                "        function submitLogin() {\n" +
                "            const url = document.getElementById('url').value;\n" +
                "            const username = document.getElementById('username').value;\n" +
                "            const password = document.getElementById('password').value;\n" +
                "            const apiType = document.querySelector('input[name=\"apiType\"]:checked').value;\n" +
                "            const btn = document.getElementById('btn');\n" +
                "            const status = document.getElementById('status');\n" +
                "\n" +
                "            if (!url) { alert('请输入服务地址'); return; }\n" +
                "            if (apiType === 'tv' && (!username || !password)) {\n" +
                "                alert('MiMusic 模式必须输入用户名和密码'); return;\n" +
                "            }\n" +
                "\n" +
                "            btn.disabled = true; btn.innerText = '正在推送...';\n" +
                "            \n" +
                "            const formData = new URLSearchParams();\n" +
                "            formData.append('url', url);\n" +
                "            formData.append('username', username);\n" +
                "            formData.append('password', password);\n" +
                "            formData.append('token', document.getElementById('token').value);\n" +
                "            formData.append('apiType', apiType);\n" +
                "\n" +
                "            fetch('/login', {\n" +
                "                method: 'POST',\n" +
                "                body: formData\n" +
                "            })\n" +
                "            .then(res => res.text())\n" +
                "            .then(data => {\n" +
                "                if (data === 'SUCCESS') {\n" +
                "                    status.style.color = '#4ade80';\n" +
                "                    status.innerText = '✅ 推送成功！电视端正在自动登录...';\n" +
                "                } else {\n" +
                "                    throw new Error(data);\n" +
                "                }\n" +
                "            })\n" +
                "            .catch(err => {\n" +
                "                status.style.color = '#f87171';\n" +
                "                status.innerText = '❌ 推送失败: ' + err.message;\n" +
                "                btn.disabled = false; btn.innerText = '重试推送';\n" +
                "            });\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
