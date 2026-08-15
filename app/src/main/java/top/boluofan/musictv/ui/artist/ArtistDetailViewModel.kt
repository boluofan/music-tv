package top.boluofan.musictv.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.api.ApiClient
import top.boluofan.musictv.data.model.AlbumItem
import top.boluofan.musictv.data.model.ArtistDetail
import top.boluofan.musictv.data.model.LibraryArtistItem
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.repository.UserRepository
import javax.inject.Inject

data class ArtistDetailUiState(
    val detail: ArtistDetail? = null,
    val songs: List<MusicInfo> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val isLoading: Boolean = true,
    val isFavorite: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    fun load(artistId: String, source: String) {
        _uiState.value = ArtistDetailUiState(isLoading = true)
        viewModelScope.launch {
            runCatching {
                val detail = ApiClient.getMusicApi().getArtistDetail(source, artistId)
                val songs = ApiClient.getMusicApi().getArtistSongs(source, artistId)
                val albums = ApiClient.getMusicApi().getArtistAlbums(source, artistId, page = 1).list.orEmpty()
                val isFavorite = userRepository.getLibraryArtists()
                    .any { it.id == artistId && it.source == source }
                ArtistDetailUiState(
                    detail = detail,
                    songs = songs,
                    albums = albums,
                    isFavorite = isFavorite,
                    isLoading = false
                )
            }.fold(
                onSuccess = { state -> _uiState.value = state },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun toggleFavorite(artistId: String, source: String, name: String?, avatar: String?) {
        viewModelScope.launch {
            runCatching {
                userRepository.toggleArtist(
                    LibraryArtistItem(id = artistId, name = name, source = source, avatar = avatar)
                )
            }.onSuccess { nowFavorite ->
                _uiState.value = _uiState.value.copy(isFavorite = nowFavorite)
            }
        }
    }

    fun clear() {
        _uiState.value = ArtistDetailUiState()
    }
}
