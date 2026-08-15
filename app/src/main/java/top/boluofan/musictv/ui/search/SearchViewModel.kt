package top.boluofan.musictv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.api.ApiClient
import top.boluofan.musictv.data.model.AlbumItem
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.model.Playlist
import top.boluofan.musictv.data.model.SearchArtistItem
import javax.inject.Inject

enum class SearchType(val label: String, val apiType: String) {
    SONG("歌曲", "song"),
    SINGER("歌手", "singer"),
    ALBUM("专辑", "album"),
    PLAYLIST("歌单", "playlist")
}

data class SearchUiState(
    val query: String = "",
    val type: SearchType = SearchType.SONG,
    val source: String = "kw",
    val hotTags: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    val songResults: List<MusicInfo> = emptyList(),
    val singerResults: List<SearchArtistItem> = emptyList(),
    val albumResults: List<AlbumItem> = emptyList(),
    val playlistResults: List<Playlist> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null
)

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
        _uiState.value = _uiState.value.copy(source = source)
        loadHotTags()
        if (_uiState.value.query.isNotBlank()) search()
    }

    fun selectType(type: SearchType) {
        if (_uiState.value.type == type) return
        _uiState.value = _uiState.value.copy(type = type, hasSearched = false)
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
                    SearchType.PLAYLIST -> ApiClient.getMusicApi().searchPlaylists(
                        name = keyword, source = state.source, page = 1, limit = 30
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
                        SearchType.PLAYLIST -> current.copy(playlistResults = result as List<Playlist>, isSearching = false)
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
            albumResults = emptyList(), playlistResults = emptyList(), error = null
        )
    }

    private fun friendlySearchError(e: Throwable, type: SearchType, source: String): String {
        val serverMsg = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
        if (serverMsg != null && serverMsg.contains("does not support")) {
            return "音乐源 $source 不支持${type.label}搜索，请切换音乐源（如 tx/wy）"
        }
        return e.message ?: "搜索失败"
    }
}
