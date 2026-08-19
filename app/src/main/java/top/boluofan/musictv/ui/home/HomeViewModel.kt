package top.boluofan.musictv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.model.LibraryAlbumItem
import top.boluofan.musictv.data.model.LibraryArtistItem
import top.boluofan.musictv.data.model.ListData
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.model.Playlist
import top.boluofan.musictv.data.repository.UserRepository
import javax.inject.Inject

data class HomeUiState(
    val defaultSongs: List<MusicInfo> = emptyList(),
    val loveSongs: List<MusicInfo> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val libraryArtists: List<LibraryArtistItem> = emptyList(),
    val libraryAlbums: List<LibraryAlbumItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val hadData = _uiState.value.playlists.isNotEmpty()
        _uiState.value = _uiState.value.copy(isLoading = !hadData, isRefreshing = hadData, error = null)
        viewModelScope.launch {
            runCatching { userRepository.getUserList() }.fold(
                onSuccess = { list ->
                    val artists = runCatching { userRepository.getLibraryArtists() }.getOrDefault(emptyList())
                    val albums = runCatching { userRepository.getLibraryAlbums() }.getOrDefault(emptyList())
                    applyListData(list, artists, albums)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message
                    )
                }
            )
        }
    }

    private fun applyListData(
        list: ListData,
        artists: List<LibraryArtistItem>,
        albums: List<LibraryAlbumItem>
    ) {
        _uiState.value = _uiState.value.copy(
            defaultSongs = list.defaultList.orEmpty(),
            loveSongs = list.loveList.orEmpty(),
            playlists = list.userList.orEmpty(),
            libraryArtists = artists,
            libraryAlbums = albums,
            isLoading = false,
            isRefreshing = false,
            error = null
        )
    }

    fun removeArtist(item: LibraryArtistItem) {
        viewModelScope.launch {
            val stillFavorite = runCatching { userRepository.toggleArtist(item) }.getOrDefault(true)
            if (!stillFavorite) {
                _uiState.update {
                    it.copy(libraryArtists = it.libraryArtists.filterNot { a -> a.id == item.id && a.source == item.source })
                }
            }
        }
    }

    fun removeAlbum(item: LibraryAlbumItem) {
        viewModelScope.launch {
            val stillFavorite = runCatching { userRepository.toggleAlbum(item) }.getOrDefault(true)
            if (!stillFavorite) {
                _uiState.update {
                    it.copy(libraryAlbums = it.libraryAlbums.filterNot { a -> a.id == item.id && a.source == item.source })
                }
            }
        }
    }
}
