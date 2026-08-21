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
import top.boluofan.musictv.data.model.AlbumItem
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.model.Playlist
import top.boluofan.musictv.data.model.SearchArtistItem
import top.boluofan.musictv.data.model.SongListPageResponse
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

    init {
        loadHotTags()
    }

    fun loadHotTags() {
        viewModelScope.launch {
            runCatching { ApiClient.getMusicApi().getHotSearch(_uiState.value.source) }
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(hotTags = resp.list.orEmpty())
                }
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
        _uiState.value = _uiState.value.copy(type = type, source = source, hasSearched = false)
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
            runCatching {
                when (state.type) {
                    SearchType.SONG -> ApiClient.getMusicApi().search(
                        name = keyword, source = state.source, page = 1, limit = 20,
                        type = "song", pages = 3
                    )
                    SearchType.SINGER -> ApiClient.getMusicApi().searchSingers(
                        name = keyword, source = state.source, page = 1, limit = 30
                    )
                    SearchType.ALBUM -> ApiClient.getMusicApi().searchAlbums(
                        name = keyword, source = state.source, page = 1, limit = 30
                    )
                    SearchType.PLAYLIST -> ApiClient.getMusicApi().searchSongList(
                        source = state.source, text = keyword, page = state.playlistPage
                    )
                }
            }.fold(
                onSuccess = { result ->
                    val current = _uiState.value
                    @Suppress("UNCHECKED_CAST")
                    _uiState.value = when (current.type) {
                        SearchType.SONG -> current.copy(songResults = result as List<MusicInfo>, isSearching = false)
                        SearchType.SINGER -> current.copy(singerResults = result as List<SearchArtistItem>, isSearching = false)
                        SearchType.ALBUM -> current.copy(albumResults = result as List<AlbumItem>, isSearching = false)
                        SearchType.PLAYLIST -> current.copy(
                            playlistResults = (result as SongListPageResponse).list ?: emptyList(),
                            isSearching = false
                        )
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
    }
}
