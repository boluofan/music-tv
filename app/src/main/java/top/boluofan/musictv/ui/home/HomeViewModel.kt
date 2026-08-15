package top.boluofan.musictv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.model.ListData
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.model.Playlist
import top.boluofan.musictv.data.repository.UserRepository
import javax.inject.Inject

data class HomeUiState(
    val defaultSongs: List<MusicInfo> = emptyList(),
    val loveSongs: List<MusicInfo> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
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
        _uiState.value = _uiState.value.copy(isLoading = _uiState.value.playlists.isEmpty(), isRefreshing = _uiState.value.playlists.isNotEmpty(), error = null)
        viewModelScope.launch {
            runCatching { userRepository.getUserList() }.fold(
                onSuccess = { list -> applyListData(list) },
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

    private fun applyListData(list: ListData) {
        val playlists = list.userList.orEmpty()
        _uiState.value = _uiState.value.copy(
            defaultSongs = list.defaultList.orEmpty(),
            loveSongs = list.loveList.orEmpty(),
            playlists = playlists,
            isLoading = false,
            isRefreshing = false,
            error = null
        )
    }
}
