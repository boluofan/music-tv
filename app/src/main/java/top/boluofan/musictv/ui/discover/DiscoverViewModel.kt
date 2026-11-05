package top.boluofan.musictv.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.api.ApiClient
import top.boluofan.musictv.data.model.BoardItem
import top.boluofan.musictv.data.model.Playlist
import top.boluofan.musictv.data.model.TagGroup
import javax.inject.Inject

enum class DiscoverSection(val label: String) {
    SQUARE("歌单广场"),
    LEADERBOARD("排行榜")
}

data class DiscoverUiState(
    val section: DiscoverSection = DiscoverSection.SQUARE,
    val source: String = "tx",
    val selectedTagId: String? = null,
    val squareTags: List<TagGroup> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val boards: List<BoardItem> = emptyList(),
    // 榜单封面：key 为 bangid（或 id），值为榜单第一首歌的封面地址，异步补全
    val boardCovers: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DiscoverViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var coverJob: Job? = null
    // 按平台缓存已取到的榜单封面，来回切换平台时不重复请求
    private val boardCoverCache = HashMap<String, MutableMap<String, String>>()

    init {
        loadSection(DiscoverSection.SQUARE, initial = true)
    }

    fun switchSection(section: DiscoverSection) {
        if (_uiState.value.section == section) return
        _uiState.value = _uiState.value.copy(section = section, isLoading = true, error = null)
        loadSection(section)
    }

    fun selectSource(source: String) {
        if (_uiState.value.source == source) return
        _uiState.value = _uiState.value.copy(source = source, selectedTagId = null, isLoading = true, error = null)
        loadSection(_uiState.value.section)
    }

    fun selectTag(tagId: String?) {
        val state = _uiState.value
        if (state.section != DiscoverSection.SQUARE) return
        if (state.selectedTagId == tagId) return
        _uiState.value = state.copy(
            selectedTagId = tagId,
            playlists = emptyList(),
            page = 1,
            hasMore = false,
            isLoading = true,
            error = null
        )
        loadJob = viewModelScope.launch {
            loadSquare(tagId, page = 1, append = false)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        if (loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true)
            when (state.section) {
                DiscoverSection.SQUARE -> loadSquare(state.selectedTagId, state.page + 1, append = true)
                DiscoverSection.LEADERBOARD -> Unit
            }
            _uiState.value = _uiState.value.copy(isLoadingMore = false)
        }
    }

    fun refresh() {
        val state = _uiState.value
        if (state.isLoading) return
        loadSection(state.section)
    }

    private fun loadSection(section: DiscoverSection, initial: Boolean = false) {
        loadJob?.cancel()
        coverJob?.cancel()
        when (section) {
            DiscoverSection.SQUARE -> {
                _uiState.value = _uiState.value.copy(playlists = emptyList(), page = 1, hasMore = false, isLoading = true, error = null)
                loadTags { loadSquare(_uiState.value.selectedTagId, page = 1, append = false) }
            }
            DiscoverSection.LEADERBOARD -> loadBoards()
        }
    }

    private fun loadTags(onDone: suspend () -> Unit) {
        val source = _uiState.value.source
        loadJob = viewModelScope.launch {
            runCatching { ApiClient.getMusicApi().getSongListTags(source) }
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        squareTags = resp.tags.orEmpty().flatMap { it.list.orEmpty() }.distinctBy { it.id }
                            .plus(resp.hotTag.orEmpty().map { TagGroup(id = it.id, name = it.name) })
                    )
                }
            onDone()
        }
    }

    private suspend fun loadSquare(tagId: String?, page: Int, append: Boolean) {
        val source = _uiState.value.source
        runCatching {
            ApiClient.getMusicApi().getSongListList(source = source, tagId = tagId, sortId = null, page = page)
        }.fold(
            onSuccess = { resp ->
                val current = _uiState.value
                val merged = if (append) current.playlists + resp.list.orEmpty() else resp.list.orEmpty()
                _uiState.value = current.copy(
                    playlists = merged.distinctBy { it.id },
                    page = page,
                    hasMore = page * (resp.limit.takeIf { it > 0 } ?: 30) < resp.total,
                    isLoading = false,
                    error = null
                )
            },
            onFailure = { e ->
                val current = _uiState.value
                _uiState.value = current.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = if (current.playlists.isEmpty()) e.message else null
                )
            }
        )
    }

    private fun loadBoards() {
        val source = _uiState.value.source
        loadJob = viewModelScope.launch {
            runCatching { ApiClient.getMusicApi().getLeaderboardBoards(source) }.fold(
                onSuccess = { resp ->
                    val boards = resp.list.orEmpty()
                    _uiState.value = _uiState.value.copy(
                        boards = boards,
                        boardCovers = boardCoverCache[source].orEmpty().toMap(),
                        isLoading = false,
                        error = null
                    )
                    loadBoardCovers(source, boards)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    /**
     * 榜单接口不带封面，这里取每个榜单的第一页歌曲，用第一首有封面的歌曲作为榜单封面。
     * 限制并发为 3，取到一个就刷新一个，失败的榜单保持占位图标。
     */
    private fun loadBoardCovers(source: String, boards: List<BoardItem>) {
        coverJob?.cancel()
        val cache = boardCoverCache.getOrPut(source) { HashMap() }
        val pending = boards.filter { board ->
            val key = boardKey(board)
            key.isNotEmpty() && !board.bangid.isNullOrEmpty() && key !in cache
        }
        if (pending.isEmpty()) return
        coverJob = viewModelScope.launch {
            val semaphore = Semaphore(3)
            coroutineScope {
                pending.map { board ->
                    async {
                        semaphore.withPermit {
                            val cover = runCatching {
                                ApiClient.getMusicApi()
                                    .getLeaderboardList(source, board.bangid.orEmpty(), 1)
                                    .list.orEmpty()
                                    .take(5)
                                    .firstNotNullOfOrNull { it.picUrl?.takeIf { url -> url.isNotBlank() } }
                            }.getOrNull()
                            if (cover != null) {
                                val key = boardKey(board)
                                cache[key] = cover
                                val current = _uiState.value
                                if (current.source == source && current.section == DiscoverSection.LEADERBOARD) {
                                    _uiState.value = current.copy(boardCovers = current.boardCovers + (key to cover))
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private fun boardKey(board: BoardItem): String = board.bangid ?: board.id ?: ""
}
