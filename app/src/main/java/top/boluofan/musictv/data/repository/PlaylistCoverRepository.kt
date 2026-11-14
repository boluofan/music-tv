package top.boluofan.musictv.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.boluofan.musictv.data.model.Playlist
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 歌单封面的「歌曲封面组合」来源（应用级单例）。
 *
 * 与 songloft-tv 不同，lxserver 的 /api/user/list 直接把歌单歌曲内嵌在 Playlist.songs 里，
 * 无需额外分页请求，这里只做纯内存采样：
 * 1. **结果锁定**：组合一旦生成就写入 [covers]，除非 [invalidate] 否则不再重算，
 *    避免每次重组/进出页面封面都换一组，首页、歌单页、详情页看到的始终是同一组。
 * 2. **确定性随机**：抽取张数与洗牌以歌单 id 为种子，即使缓存被清空，
 *    只要歌单内容没变，重新生成的组合仍一致。
 *
 * 候选不足时自适应：2 张 / 1 张由 UI 决定布局，0 张则回落到内置曲库封面。
 */
@Singleton
class PlaylistCoverRepository @Inject constructor() {

    /** 歌单 id → 已锁定的封面组合；空列表表示「查过且没有可用封面」，同样不再重试 */
    private val _covers = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val covers: StateFlow<Map<String, List<String>>> = _covers.asStateFlow()

    /** 幂等触发：已缓存的歌单直接复用，不重新采样 */
    fun ensureLoaded(playlist: Playlist) {
        val id = playlist.id?.takeIf { it.isNotBlank() } ?: return
        synchronized(this) {
            if (_covers.value.containsKey(id)) return
            val candidates = playlist.songs.orEmpty()
                .mapNotNull { song -> song.picUrl?.takeIf { it.isNotBlank() } }
                .distinct()
            _covers.value = _covers.value + (id to pick(candidates, id))
        }
    }

    /** 切换服务器 / 登出时丢弃已锁定组合（种子固定，歌单未变时结果仍一致） */
    fun invalidate() {
        synchronized(this) { _covers.value = emptyMap() }
    }

    /** 随机抽 3~4 张；候选不足时按现有张数自适应 */
    private fun pick(candidates: List<String>, id: String): List<String> {
        if (candidates.isEmpty()) return emptyList()
        val random = Random(id.hashCode().toLong() xor PICK_SALT)
        val count = if (candidates.size >= MAX_COVERS) {
            random.nextInt(MIN_COVERS, MAX_COVERS + 1)
        } else {
            minOf(MIN_COVERS, candidates.size)
        }
        return candidates.shuffled(random).take(count)
    }

    companion object {
        /** 组合目标张数：随机 3 或 4 张，优先 4 张田字格 */
        const val MIN_COVERS = 3
        const val MAX_COVERS = 4
        private const val PICK_SALT = 0x2545F4914F6CDD1DL
    }
}
