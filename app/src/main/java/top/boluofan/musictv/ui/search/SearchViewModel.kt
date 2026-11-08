package top.boluofan.musictv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.api.ApiClient
import top.boluofan.musictv.data.api.QqSingerApi
import top.boluofan.musictv.data.model.AlbumItem
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.model.Playlist
import top.boluofan.musictv.data.model.SearchArtistItem
import top.boluofan.musictv.ui.config.ConfigWebServer
import javax.inject.Inject

enum class SearchType(val label: String, val apiType: String) {
    SONG("歌曲", "song"),
    SINGER("歌手", "singer"),
    ALBUM("专辑", "album"),
    PLAYLIST("歌单", "playlist")
}

private val SOURCE_SUPPORTED_TYPES: Map<String, List<SearchType>> = mapOf(
    "kw" to listOf(SearchType.SONG, SearchType.PLAYLIST),
    "kg" to listOf(SearchType.SONG, SearchType.PLAYLIST),
    "tx" to listOf(SearchType.SONG, SearchType.SINGER, SearchType.ALBUM, SearchType.PLAYLIST),
    "wy" to listOf(SearchType.SONG, SearchType.SINGER, SearchType.ALBUM, SearchType.PLAYLIST),
    "mg" to listOf(SearchType.SONG, SearchType.PLAYLIST)
)

fun supportedTypesForSource(source: String): List<SearchType> =
    SOURCE_SUPPORTED_TYPES[source] ?: listOf(SearchType.SONG)

fun supportedSourcesForType(type: SearchType): List<String> =
    SOURCE_SUPPORTED_TYPES.filter { type in it.value }.keys.toList()

data class SearchUiState(
    val query: String = "",
    val type: SearchType = SearchType.SONG,
    val source: String = "tx",
    val hotTags: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    val songResults: List<MusicInfo> = emptyList(),
    val singerResults: List<SearchArtistItem> = emptyList(),
    val albumResults: List<AlbumItem> = emptyList(),
    val playlistResults: List<Playlist> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null
) {
    // 歌单搜索需要额外的分页参数
    val playlistPage: Int = 1
}

@HiltViewModel
class SearchViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var tipJob: Job? = null
    private var hotTagsJob: Job? = null

    /** QQ 热门歌手与当前音乐源无关，切页签时不必重复请求第三方 */
    private var singerHotCache: List<String>? = null

    init {
        loadHotTags()
    }

    /**
     * 热门推荐词：
     * - 「歌手」页用 QQ 音乐热门歌手（参考 PR #21），失败时回退到 lxserver 热搜；
     * - 其它页仍用 lxserver 各音源的官方热搜。
     */
    fun loadHotTags() {
        val requestType = _uiState.value.type
        val requestSource = _uiState.value.source
        hotTagsJob?.cancel()
        hotTagsJob = viewModelScope.launch {
            if (requestType == SearchType.SINGER) {
                val singers = singerHotCache
                    ?: runCatching { QqSingerApi.hotSingers() }
                        .getOrNull()?.takeIf { it.isNotEmpty() }
                        ?.also { singerHotCache = it }
                if (singers != null) {
                    applyHotTags(requestType, requestSource, singers)
                    return@launch
                }
            }
            runCatching { ApiClient.getMusicApi().getHotSearch(requestSource) }
                .onSuccess { resp -> applyHotTags(requestType, requestSource, resp.list.orEmpty()) }
        }
    }

    // 请求返回时若用户已切换类型/平台，丢弃过期结果
    private fun applyHotTags(type: SearchType, source: String, tags: List<String>) {
        val state = _uiState.value
        if (state.type == type && state.source == source) {
            _uiState.value = state.copy(hotTags = tags)
        }
    }

    fun selectSource(source: String) {
        if (_uiState.value.source == source) return
        val supported = supportedTypesForSource(source)
        val type = if (_uiState.value.type in supported) _uiState.value.type else supported.first()
        _uiState.value = _uiState.value.copy(source = source, type = type)
        loadHotTags()
        if (_uiState.value.query.isNotBlank()) search()
    }

    fun selectType(type: SearchType) {
        if (_uiState.value.type == type) return
        val supported = supportedSourcesForType(type)
        val source = if (_uiState.value.source in supported) _uiState.value.source
        else supported.firstOrNull() ?: _uiState.value.source
        val oldType = _uiState.value.type
        // 进入/离开「歌手」时推荐词数据源不同，先清空避免短暂显示上一次的内容
        val clearHotTags = type == SearchType.SINGER || oldType == SearchType.SINGER
        _uiState.value = _uiState.value.copy(
            type = type, source = source, hasSearched = false,
            hotTags = if (clearHotTags) emptyList() else _uiState.value.hotTags
        )
        if (clearHotTags) loadHotTags()
        if (_uiState.value.query.isNotBlank()) search()
    }

    fun onQueryChanged(query: String) {
        val state = _uiState.value
        val trimmed = query.trim()
        _uiState.value = state.copy(
            query = query,
            tips = if (trimmed.length >= 1) state.tips else emptyList()
        )
        // 输入防抖拉提示词
        tipJob?.cancel()
        if (trimmed.length >= 1) {
            tipJob = viewModelScope.launch {
                delay(300)
                runCatching {
                    ApiClient.getMusicApi().getTipSearch(trimmed, state.source)
                }.onSuccess { tips ->
                    _uiState.value = _uiState.value.copy(tips = tips.orEmpty())
                }
            }
        }
    }

    fun search() {
        val state = _uiState.value
        val keyword = state.query.trim()
        if (keyword.isBlank()) return
        searchJob?.cancel()
        _uiState.value = state.copy(isSearching = true, hasSearched = true, error = null)
        searchJob = viewModelScope.launch {
            runCatching { performSearch(state, keyword) }
                .fold(
                    onSuccess = { outcome ->
                        val current = _uiState.value
                        _uiState.value = when (current.type) {
                            SearchType.SONG -> current.copy(songResults = outcome.songs, isSearching = false)
                            SearchType.SINGER -> current.copy(singerResults = outcome.singers, isSearching = false)
                            SearchType.ALBUM -> current.copy(albumResults = outcome.albums, isSearching = false)
                            SearchType.PLAYLIST -> current.copy(playlistResults = outcome.playlists, isSearching = false)
                        }
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(
                            isSearching = false,
                            error = friendlySearchError(e, state.type, state.source)
                        )
                    }
                )
        }
    }

    /**
     * 按搜索类型取结果，统一产出一个 [SearchOutcome]（不再依赖跨分支的泛型强转）。
     *
     * 歌曲搜索同时照顾两代服务端，且以「响应内容」而非版本号判断，双方都不退化：
     * - 旧版（<= 2.1.2）：`pages=3&limit=20` 一次聚合 3 页 → 60 条裸数组；
     * - 新版（>= 2.1.3）：`pages` 被忽略、带 `limit` 只给单页 20 条对象，
     *   且 `stopReason == "single-page"`。命中时再发一次不带 `limit` 的请求，
     *   让服务端聚合并返回全量结果。
     */
    private suspend fun performSearch(state: SearchUiState, keyword: String): SearchOutcome {
        val api = ApiClient.getMusicApi()
        return when (state.type) {
            SearchType.SONG -> {
                var envelope = api.search(
                    name = keyword, source = state.source, page = 1, limit = 20,
                    type = "song", pages = 3
                )
                if (envelope.stopReason == "single-page") {
                    // 补拉失败（网络抖动/超时）时保留第一次的 20 条，不让搜索整体失败
                    envelope = runCatching {
                        api.search(
                            name = keyword, source = state.source, page = 1,
                            limit = null, type = "song", pages = null
                        )
                    }.getOrDefault(envelope)
                }
                SearchOutcome(songs = envelope.items.take(MAX_SONG_RESULTS))
            }
            SearchType.SINGER -> SearchOutcome(
                singers = api.searchSingers(
                    name = keyword, source = state.source, page = 1, limit = 30
                ).items
            )
            SearchType.ALBUM -> SearchOutcome(
                albums = api.searchAlbums(
                    name = keyword, source = state.source, page = 1, limit = 30
                ).items
            )
            SearchType.PLAYLIST -> SearchOutcome(
                playlists = api.searchSongList(
                    source = state.source, text = keyword, page = state.playlistPage
                ).list.orEmpty()
            )
        }
    }

    private data class SearchOutcome(
        val songs: List<MusicInfo> = emptyList(),
        val singers: List<SearchArtistItem> = emptyList(),
        val albums: List<AlbumItem> = emptyList(),
        val playlists: List<Playlist> = emptyList()
    )

    fun clearSearch() {
        searchJob?.cancel()
        tipJob?.cancel()
        _uiState.value = _uiState.value.copy(
            query = "", tips = emptyList(), isSearching = false, hasSearched = false,
            songResults = emptyList(), singerResults = emptyList(),
            albumResults = emptyList(), error = null
        )
    }

    private var remoteServer: SearchWebServer? = null

    private val _remoteUrl = MutableStateFlow<String?>(null)
    val remoteUrl: StateFlow<String?> = _remoteUrl.asStateFlow()

    private val _remoteSubmitEvents = MutableSharedFlow<Unit>()
    val remoteSubmitEvents: SharedFlow<Unit> = _remoteSubmitEvents.asSharedFlow()

    fun startRemoteInput() {
        if (remoteServer != null) return
        val ip = ConfigWebServer.localIpAddress() ?: return
        for (port in REMOTE_PORTS) {
            val server = SearchWebServer(port) { keyword ->
                viewModelScope.launch {
                    onQueryChanged(keyword)
                    search()
                    _remoteSubmitEvents.emit(Unit)
                }
            }
            if (runCatching { server.start() }.isSuccess) {
                remoteServer = server
                _remoteUrl.value = "http://$ip:$port"
                return
            }
        }
    }

    fun stopRemoteInput() {
        remoteServer?.stop()
        remoteServer = null
        _remoteUrl.value = null
    }

    override fun onCleared() {
        stopRemoteInput()
    }

    private fun friendlySearchError(e: Throwable, type: SearchType, source: String): String {
        val serverMsg = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
        if (serverMsg != null && serverMsg.contains("does not support")) {
            return "音乐源 $source 不支持${type.label}搜索，请切换音乐源（如 tx/wy）"
        }
        return when (type) {
            SearchType.PLAYLIST -> "歌单搜索失败：${e.message ?: "未知错误"}"
            else -> e.message ?: "搜索失败"
        }
    }

    companion object {
        private val REMOTE_PORTS = intArrayOf(18903, 18904, 18905, 18906)
        // 与旧版「3 页 x 20 条」的结果量保持一致；新版后端不带 limit 时最多可回 4000 条，
        // 全塞进电视列表会拖慢渲染，这里本地截断。
        private const val MAX_SONG_RESULTS = 60
    }
}
