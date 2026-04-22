package top.boluofan.musictv;

import android.content.Context;
import android.util.Log;

import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SearchWebServer extends NanoHTTPD {
    private static final String TAG = "SearchWebServer";
    private Context context;
    private OnSearchDataReceivedListener listener;

    public interface OnSearchDataReceivedListener {
        void onSearchReceived(String keyword, String source);
    }

    public SearchWebServer(Context context, int port, OnSearchDataReceivedListener listener) {
        super(port);
        this.context = context;
        this.listener = listener;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        if (Method.GET.equals(method) && "/".equals(uri)) {
            return newFixedLengthResponse(getHtml());
        }

        if (Method.POST.equals(method) && "/search".equals(uri)) {
            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                Map<String, String> params = session.getParms();
                
                String keyword = params.get("keyword");
                String source = params.get("source");

                if (listener != null) {
                    listener.onSearchReceived(keyword, source);
                }

                return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "SUCCESS");
            } catch (IOException | NanoHTTPD.ResponseException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "ERROR: " + e.getMessage());
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
    }

    private String getHtml() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>菠萝音乐 - 扫码搜索</title>\n" +
                "    <style>\n" +
                "        body { font-family: -apple-system, sans-serif; background: #0f172a; color: white; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }\n" +
                "        .card { background: #1e293b; padding: 24px; border-radius: 16px; width: 100%; max-width: 400px; box-shadow: 0 10px 25px rgba(0,0,0,0.3); }\n" +
                "        h2 { margin-top: 0; text-align: center; color: #38bdf8; }\n" +
                "        .desc { text-align: center; color: #94a3b8; font-size: 14px; margin-bottom: 24px; }\n" +
                "        .field { margin-bottom: 16px; }\n" +
                "        label { display: block; margin-bottom: 6px; color: #94a3b8; font-size: 13px; }\n" +
                "        input, select { width: 100%; padding: 12px; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: white; box-sizing: border-box; font-size: 16px; }\n" +
                "        button { width: 100%; padding: 14px; border-radius: 8px; border: none; background: #38bdf8; color: #0f172a; font-weight: bold; font-size: 16px; cursor: pointer; margin-top: 8px; }\n" +
                "        #status { text-align: center; margin-top: 16px; font-size: 14px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"card\">\n" +
                "        <h2>洛雪音乐 TV - 扫码搜索</h2>\n" +
                "        <p class=\"desc\">输入关键词搜索，音乐将推送到电视播放</p>\n" +
                "        <div class=\"field\">\n" +
                "            <label>选择平台</label>\n" +
                "            <select id=\"source\">\n" +
                "                <option value=\"all\">聚合搜索</option>\n" +
                "                <option value=\"kw\">小窝音乐</option>\n" +
                "                <option value=\"kg\">小枸音乐</option>\n" +
                "                <option value=\"tx\">小秋音乐</option>\n" +
                "                <option value=\"wy\">小芸音乐</option>\n" +
                "                <option value=\"mg\">小蜜音乐</option>\n" +
                "            </select>\n" +
                "        </div>\n" +
                "        <div class=\"field\">\n" +
                "            <label>搜索关键词</label>\n" +
                "            <input type=\"text\" id=\"keyword\" placeholder=\"歌曲名、歌手名...\" required>\n" +
                "        </div>\n" +
                "        <button onclick=\"submitSearch()\" id=\"btn\">推送到电视</button>\n" +
                "        <div id=\"status\"></div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        function submitSearch() {\n" +
                "            const keyword = document.getElementById('keyword').value;\n" +
                "            const source = document.getElementById('source').value;\n" +
                "            const btn = document.getElementById('btn');\n" +
                "            const status = document.getElementById('status');\n" +
                "\n" +
                "            if (!keyword) { alert('请输入搜索关键词'); return; }\n" +
                "\n" +
                "            btn.disabled = true; btn.innerText = '正在推送...';\n" +
                "            \n" +
                "            const formData = new URLSearchParams();\n" +
                "            formData.append('keyword', keyword);\n" +
                "            formData.append('source', source);\n" +
                "\n" +
                "            fetch('/search', {\n" +
                "                method: 'POST',\n" +
                "                body: formData\n" +
                "            })\n" +
                "            .then(res => res.text())\n" +
                "            .then(data => {\n" +
                "                if (data === 'SUCCESS') {\n" +
                "                    status.style.color = '#4ade80';\n" +
                "                    status.innerText = '✅ 推送成功！电视正在播放...';\n" +
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
