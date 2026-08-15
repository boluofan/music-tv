package top.boluofan.musictv.ui.my

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
import top.boluofan.musictv.data.repository.UserRepository
import javax.inject.Inject

data class MyUiState(
    val selectedTab: Int = 0,
    val artists: List<LibraryArtistItem> = emptyList(),
    val albums: List<LibraryAlbumItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MyViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()

    private var loadedTab = -1

    init {
        load()
    }

    fun selectTab(tab: Int) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab != loadedTab) load()
    }

    fun load() {
        val tab = _uiState.value.selectedTab
        if (tab == loadedTab) return
        loadedTab = tab
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (tab == 0) {
                runCatching { userRepository.getLibraryArtists() }
                    .onSuccess { list ->
                        _uiState.update { it.copy(artists = list, isLoading = false) }
                    }
                    .onFailure { e ->
                        loadedTab = -1
                        _uiState.update { it.copy(error = e.message ?: "加载失败", isLoading = false) }
                    }
            } else {
                runCatching { userRepository.getLibraryAlbums() }
                    .onSuccess { list ->
                        _uiState.update { it.copy(albums = list, isLoading = false) }
                    }
                    .onFailure { e ->
                        loadedTab = -1
                        _uiState.update { it.copy(error = e.message ?: "加载失败", isLoading = false) }
                    }
            }
        }
    }

    fun removeArtist(item: LibraryArtistItem) {
        viewModelScope.launch {
            val stillFavorite = runCatching { userRepository.toggleArtist(item) }.getOrDefault(true)
            if (!stillFavorite) {
                _uiState.update { it.copy(artists = it.artists.filterNot { a -> a.id == item.id && a.source == item.source }) }
            }
        }
    }

    fun removeAlbum(item: LibraryAlbumItem) {
        viewModelScope.launch {
            val stillFavorite = runCatching { userRepository.toggleAlbum(item) }.getOrDefault(true)
            if (!stillFavorite) {
                _uiState.update { it.copy(albums = it.albums.filterNot { a -> a.id == item.id && a.source == item.source }) }
            }
        }
    }
}
