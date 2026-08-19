package top.boluofan.musictv.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.api.ApiClient
import top.boluofan.musictv.data.model.LibraryAlbumItem
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.repository.UserRepository
import javax.inject.Inject

data class AlbumDetailUiState(
    val albumName: String = "",
    val singer: String? = null,
    val cover: String? = null,
    val songs: List<MusicInfo> = emptyList(),
    val total: Int = 0,
    val isLoading: Boolean = true,
    val isFavorite: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    fun load(albumId: String, source: String, albumName: String?, singer: String?, cover: String?) {
        _uiState.value = AlbumDetailUiState(albumName = albumName.orEmpty(), singer = singer, cover = cover, isLoading = true)
        viewModelScope.launch {
            runCatching {
                val resp = ApiClient.getMusicApi().getAlbumSongs(source, albumId)
                val isFavorite = userRepository.getLibraryAlbums()
                    .any { it.id == albumId && it.source == source }
                resp to isFavorite
            }.fold(
                onSuccess = { (resp, isFavorite) ->
                    val info = resp.list?.firstOrNull()
                    _uiState.value = _uiState.value.copy(
                        albumName = resp.name?.takeIf { it.isNotEmpty() } ?: albumName.orEmpty(),
                        singer = singer ?: info?.singer,
                        cover = cover ?: info?.img,
                        songs = resp.list.orEmpty(),
                        total = resp.total,
                        isFavorite = isFavorite,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun toggleFavorite(albumId: String, source: String, name: String?, singer: String?, img: String?) {
        viewModelScope.launch {
            runCatching {
                userRepository.toggleAlbum(
                    LibraryAlbumItem(id = albumId, name = name, source = source, singer = singer, img = img)
                )
            }.onSuccess { nowFavorite ->
                _uiState.value = _uiState.value.copy(isFavorite = nowFavorite)
            }
        }
    }

    fun clear() {
        _uiState.value = AlbumDetailUiState()
    }
}
