package top.boluofan.musictv.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.api.ApiClient
import top.boluofan.musictv.data.model.MusicInfo
import javax.inject.Inject

data class LeaderboardDetailUiState(
    val songs: List<MusicInfo> = emptyList(),
    val total: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class LeaderboardDetailViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardDetailUiState())
    val uiState: StateFlow<LeaderboardDetailUiState> = _uiState.asStateFlow()

    fun load(bangid: String, source: String) {
        _uiState.value = LeaderboardDetailUiState(isLoading = true)
        viewModelScope.launch {
            runCatching {
                var page = 1
                val songs = mutableListOf<MusicInfo>()
                var total = 0
                do {
                    val resp = ApiClient.getMusicApi().getLeaderboardList(source, bangid, page)
                    val list = resp.list.orEmpty()
                    songs += list
                    total = resp.total
                    page++
                } while (list.isNotEmpty() && songs.size < total)
                songs to total
            }.fold(
                onSuccess = { (songs, total) ->
                    _uiState.value = _uiState.value.copy(songs = songs, total = total, isLoading = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun clear() {
        _uiState.value = LeaderboardDetailUiState()
    }
}
