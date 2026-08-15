package top.boluofan.musictv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.model.Playlist
import top.boluofan.musictv.data.repository.UserRepository
import javax.inject.Inject

data class AddToPlaylistUiState(
    val target: MusicInfo? = null,
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val addedName: String? = null
)

@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddToPlaylistUiState())
    val uiState: StateFlow<AddToPlaylistUiState> = _uiState.asStateFlow()

    fun show(music: MusicInfo) {
        _uiState.value = _uiState.value.copy(target = music, isLoading = true, addedName = null)
        viewModelScope.launch {
            runCatching { userRepository.getUserList().userList.orEmpty() }
                .onSuccess { lists ->
                    _uiState.value = _uiState.value.copy(playlists = lists, isLoading = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(playlists = emptyList(), isLoading = false)
                }
        }
    }

    fun addTo(playlist: Playlist) {
        val music = _uiState.value.target ?: return
        viewModelScope.launch {
            runCatching { userRepository.addToUserList(playlist.id.orEmpty(), listOf(music)) }
            _uiState.value = _uiState.value.copy(
                target = null,
                playlists = emptyList(),
                addedName = playlist.name
            )
        }
    }

    fun dismiss() {
        _uiState.value = _uiState.value.copy(target = null, playlists = emptyList())
    }

    fun dismissAdded() {
        _uiState.value = _uiState.value.copy(addedName = null)
    }
}

/**
 * 加歌到歌单宿主：任意屏幕放一个即可，通过 [viewModel.show] 打开选歌单弹窗，
 * 添加成功后短暂显示"已添加到 X"提示。
 */
@Composable
fun AddToPlaylistHost(
    viewModel: AddToPlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.target != null) {
        AddToPlaylistDialog(
            playlists = uiState.playlists,
            isLoading = uiState.isLoading,
            onSelect = { viewModel.addTo(it) },
            onDismiss = { viewModel.dismiss() }
        )
    }

    val addedName = uiState.addedName
    if (addedName != null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 48.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "已添加到「$addedName」",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
        LaunchedEffect(addedName) {
            delay(1800)
            viewModel.dismissAdded()
        }
    }
}
