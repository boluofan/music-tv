package top.boluofan.musictv.data.config

import android.util.Log
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import top.boluofan.musictv.data.model.MusicInfo

/**
 * 扫码点歌服务（K 歌模式专用）：
 * - 手机扫码进入「点歌」页面，可搜索 5 个平台（tx/kw/kg/wy/mg）歌曲并加入 K 歌独立歌单
 * - 端点：/、/order/search、/order/add、/order/top、/order/remove、/order/queue
 * - 端口 9089（与 music-tv 现有 ConfigWebServer 8089/9090 错开，启动失败扫描 9080-9099）
 * - 入口点歌：扫码后由 PlayerViewModel 启动；退出 K 歌时停服，二维码随 URL 失效而消失
 *
 * HTML/JS/样式照搬 songloft-tv 端 ConfigWebServer.kt:280-510 段，只改平台下拉框适配 music-tv 5 个平台。
 */
class KaraokeOrderWebServer(
    port: Int,
    private val onOrderSearch: ((keyword: String, source: String) -> List<MusicInfo>)? = null,
    private val onOrderAdd: ((song: MusicInfo) -> Unit)? = null,
    private val onOrderTop: ((index: Int) -> Unit)? = null,
    private val onOrderRemove: ((index: Int) -> Unit)? = null,
    private val onOrderQueue: (() -> List<MusicInfo>)? = null
) : NanoHTTPD(port) {

    private val gson = Gson()

    override fun serve(session: IHTTPSession): Response {
        // 当前 K 歌歌单（禁用缓存：手机端 5 秒轮询需实时反映已唱移除/置顶变化）
        if (session.method == Method.GET && session.uri.startsWith("/order/queue")) {
            val queue = onOrderQueue?.invoke().orEmpty()
            val json = queue.mapIndexed { index, song ->
                """{"index":$index,"title":${esc(song.name)},"artist":${esc(song.singer)},"id":${esc(song.songId)},"source":${esc(song.source)}}"""
            }.joinToString(",", "[", "]")
            return json(Response.Status.OK, json).apply {
                addHeader("Cache-Control", "no-store")
            }
        }
        // 搜索（POST /order/search 接收 keyword + source，返回候选 List<MusicInfo> JSON）
        if (session.method == Method.POST && session.uri == "/order/search") {
            session.parseBody(HashMap())
            val onOrderSearch = onOrderSearch
                ?: return text(Response.Status.BAD_REQUEST, "电视端当前不在 K 歌模式，无法搜索。")
            val keyword = session.parameters["keyword"]?.firstOrNull()?.trim().orEmpty()
            val source = session.parameters["source"]?.firstOrNull()?.trim().orEmpty()
            if (keyword.isBlank()) return text(Response.Status.BAD_REQUEST, "请输入搜索关键字。")
            if (source.isBlank()) return text(Response.Status.BAD_REQUEST, "请选择平台。")
            val results = runCatching { onOrderSearch(keyword, source) }
                .getOrDefault(emptyList())
            return json(Response.Status.OK, gson.toJson(results))
        }
        // 加入 K 歌歌单（POST /order/add 接收 song JSON）
        if (session.method == Method.POST && session.uri == "/order/add") {
            try {
                session.parseBody(HashMap())
            } catch (e: Throwable) {
                Log.e(TAG, "parseBody failed", e)
                return text(Response.Status.INTERNAL_ERROR, "parse error: ${e.message}")
            }
            val onOrderAdd = onOrderAdd
                ?: return text(Response.Status.BAD_REQUEST, "电视端当前不在 K 歌模式，无法点歌。")
            val songJson = session.parameters["song"]?.firstOrNull().orEmpty()
            val song = runCatching { gson.fromJson(songJson, MusicInfo::class.java) }.getOrNull()
                ?: return text(Response.Status.BAD_REQUEST, "歌曲数据无效。")
            try {
                onOrderAdd(song)
            } catch (e: Throwable) {
                Log.e(TAG, "onOrderAdd threw", e)
                return text(Response.Status.INTERNAL_ERROR, "onOrderAdd error: ${e.message}")
            }
            return text(Response.Status.OK, "已点歌：${song.name.orEmpty()}")
        }
        // 置顶（下一首演唱）
        if (session.method == Method.POST && session.uri == "/order/top") {
            session.parseBody(HashMap())
            val onOrderTop = onOrderTop
                ?: return text(Response.Status.BAD_REQUEST, "电视端当前不在 K 歌模式。")
            val index = session.parameters["index"]?.firstOrNull()?.toIntOrNull() ?: -1
            if (index < 0) return text(Response.Status.BAD_REQUEST, "无效序号。")
            onOrderTop(index)
            return text(Response.Status.OK, "已置顶。")
        }
        // 删除（移出 K 歌歌单）
        if (session.method == Method.POST && session.uri == "/order/remove") {
            session.parseBody(HashMap())
            val onOrderRemove = onOrderRemove
                ?: return text(Response.Status.BAD_REQUEST, "电视端当前不在 K 歌模式。")
            val index = session.parameters["index"]?.firstOrNull()?.toIntOrNull() ?: -1
            if (index < 0) return text(Response.Status.BAD_REQUEST, "无效序号。")
            onOrderRemove(index)
            return text(Response.Status.OK, "已删除。")
        }
        return html(Response.Status.OK, PAGE)
    }

    private fun esc(value: String?): String {
        val s = value.orEmpty()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "\"$s\""
    }

    private fun html(status: Response.Status, content: String): Response =
        newFixedLengthResponse(status, "text/html; charset=utf-8", content)

    private fun json(status: Response.Status, content: String): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", content)

    private fun text(status: Response.Status, content: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", content)

    companion object {
        private const val TAG = "KaraokeOrderWebServer"

        // 默认端口；启动失败时由 PlayerViewModel 扫描 9080-9099 找空端口
        const val DEFAULT_PORT = 9089
        const val PORT_SCAN_START = 9080
        const val PORT_SCAN_END = 9099

        // 平台 code → 显示名（与 SourceLabel.kt 保持一致）
        private val SOURCE_NAMES = linkedMapOf(
            "tx" to "小秋",
            "kw" to "小蜗",
            "kg" to "小枸",
            "wy" to "小芸",
            "mg" to "小蜜"
        )

        // HTML 模板：5 个平台下拉 + 搜索框 + 候选列表 + 当前 K 歌歌单
        // 样式/JS 结构对齐 songloft-tv ConfigWebServer.kt order 段；仅增加 source 选择
        private val PAGE = """
            <!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>菠萝音乐 - 扫码点歌</title>
            <style>
            body{font-family:-apple-system,sans-serif;background:#0f172a;color:#eee;margin:0;padding:24px}
            h2{color:#38bdf8;text-align:center;margin:0 0 16px}
            label{display:block;margin:14px 0 6px;font-size:14px;color:#94a3b8}
            select,input{width:100%;box-sizing:border-box;padding:12px;font-size:16px;
            border:1px solid #334155;border-radius:8px;background:#1e293b;color:#eee}
            button.submit{width:100%;margin-top:20px;padding:14px;font-size:16px;font-weight:bold;
            border:none;border-radius:8px;background:#38bdf8;color:#0f172a}
            #orderSearchStatus{margin-top:14px;font-size:14px;text-align:center;color:#38bdf8;min-height:20px}
            .order-sep{margin:24px 0 12px;padding-top:14px;border-top:1px solid #334155;
            font-size:14px;color:#38bdf8;text-align:center}
            #orderResults .row,#orderQueue .row{display:flex;align-items:center;justify-content:space-between;
            padding:12px;margin-bottom:8px;font-size:14px;border:1px solid #334155;border-radius:8px;
            background:#1e293b;color:#eee;text-decoration:none;gap:8px}
            #orderResults .row .meta,#orderQueue .row .meta{flex:1;min-width:0;
            overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            #orderResults .row .meta .src,#orderQueue .row .meta .src{color:#64748b;font-size:12px;margin-left:6px}
            .act{padding:8px 12px;margin-left:6px;font-size:13px;border:none;border-radius:6px;
            background:#38bdf8;color:#0f172a;cursor:pointer}
            .act.del{background:#7f1d1d;color:#fff}
            </style></head><body>
            <h2>菠萝音乐 · 扫码点歌</h2>
            <form id="orderSearchForm">
              <label>平台</label>
              <select id="orderSource" name="source">
                ${SOURCE_NAMES.entries.joinToString("") { (code, name) ->
                    """<option value="$code">$name（$code）</option>"""
                }}
              </select>
              <label>搜索歌曲</label>
              <input name="keyword" id="orderKeyword" type="text" placeholder="输入歌曲、歌手或专辑" required>
              <button class="submit" type="submit">搜索</button>
            </form>
            <div id="orderSearchStatus" class="order-hint"></div>
            <div id="orderResults"></div>
            <div class="order-sep">当前 K 歌歌单</div>
            <div id="orderQueue"><div class="order-hint">加载中…</div></div>
            <script>
            function escHtml(t){return (t==null?'':String(t)).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');}
            document.getElementById('orderSearchForm').addEventListener('submit',function(e){
              e.preventDefault();
              var status=document.getElementById('orderSearchStatus');
              var keyword=document.getElementById('orderKeyword').value.trim();
              var source=document.getElementById('orderSource').value;
              if(!keyword){status.textContent='请输入搜索关键字';status.style.color='#f87171';return;}
              if(!source){status.textContent='请选择平台';status.style.color='#f87171';return;}
              status.style.color='#38bdf8';
              status.textContent='搜索中...';
              fetch('/order/search',{method:'POST',
                headers:{'Content-Type':'application/x-www-form-urlencoded'},
                body:'keyword='+encodeURIComponent(keyword)+'&source='+encodeURIComponent(source)})
                .then(function(r){return r.json().then(function(list){
                  if(!Array.isArray(list)){status.textContent='搜索失败';status.style.color='#f87171';return;}
                  status.textContent='找到 '+list.length+' 首';
                  renderOrderResults(list);
                });})
                .catch(function(){status.textContent='搜索失败，请确认电视端在 K 歌模式';
                  status.style.color='#f87171';});
            });
            function renderOrderResults(list){
              var el=document.getElementById('orderResults');
              if(!list.length){el.innerHTML='<div class="order-hint">无结果</div>';return;}
              el.innerHTML=list.map(function(s){
                var json=JSON.stringify(s);
                return '<div class="row"><div class="meta">'+
                  escHtml(s.name||'')+' - '+escHtml(s.singer||'')+
                  '<span class="src">['+escHtml(s.source||'')+']</span></div>'+
                  '<button class="act" data-order-add="'+escHtml(json)+'">点歌</button></div>';
              }).join('');
              el.querySelectorAll('button[data-order-add]').forEach(function(btn){
                btn.addEventListener('click',function(){
                  orderAdd(btn.getAttribute('data-order-add'));
                });
              });
            }
            window.orderAdd=function(songJson){
              fetch('/order/add',{method:'POST',
                headers:{'Content-Type':'application/x-www-form-urlencoded'},
                body:'song='+encodeURIComponent(songJson)})
                .then(function(){loadOrder();})
                .catch(function(){loadOrder();});
            };
            function loadOrder(){
              fetch('/order/queue?t='+Date.now(),{cache:'no-store'}).then(function(r){return r.json();}).then(function(list){
                var el=document.getElementById('orderQueue');
                if(!list.length){el.innerHTML='<div class="order-hint">队列为空</div>';return;}
                el.innerHTML=list.map(function(s){
                  return '<div class="row" data-index="'+s.index+'">'+
                    '<div class="meta">'+escHtml(s.title)+' - '+escHtml(s.artist||'')+
                    '<span class="src">['+escHtml(s.source||'')+']</span></div>'+
                    '<div><button class="act" data-act="top">置顶</button>'+
                    '<button class="act del" data-act="remove">删除</button></div></div>';
                }).join('');
                el.querySelectorAll('.row').forEach(function(row){
                  var idx=row.getAttribute('data-index');
                  row.querySelector('button[data-act="top"]').addEventListener('click',function(){orderTop(idx);});
                  row.querySelector('button[data-act="remove"]').addEventListener('click',function(){orderRemove(idx);});
                });
              }).catch(function(){
                document.getElementById('orderQueue').innerHTML='<div class="order-hint">加载失败</div>';
              });
            }
            window.orderTop=function(index){
              fetch('/order/top',{method:'POST',
                headers:{'Content-Type':'application/x-www-form-urlencoded'},
                body:'index='+index}).then(function(){loadOrder();});
            };
            window.orderRemove=function(index){
              if(!confirm('确定要从 K 歌歌单中删除这首歌吗？'))return;
              fetch('/order/remove',{method:'POST',
                headers:{'Content-Type':'application/x-www-form-urlencoded'},
                body:'index='+index}).then(function(){loadOrder();});
            };
            setInterval(function(){loadOrder();},5000);
            loadOrder();
            </script>
            </body></html>
        """.trimIndent()
    }
}
