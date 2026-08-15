package top.boluofan.musictv.ui.discover

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.ui.components.AddToPlaylistHost
import top.boluofan.musictv.ui.components.AddToPlaylistViewModel
import top.boluofan.musictv.ui.components.SongListItem
import top.boluofan.musictv.ui.navigation.ListBackToTopHandler

@Composable
fun LeaderboardDetailScreen(
    bangid: String,
    boardName: String,
    source: String,
    viewModel: LeaderboardDetailViewModel = hiltViewModel(),
    addToPlaylistViewModel: AddToPlaylistViewModel = hiltViewModel(),
    onSongClick: (List<MusicInfo>, Int) -> Unit = { _, _ -> },
    onShufflePlay: (List<MusicInfo>) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val topFocus = remember { FocusRequester() }
    val playAllFocus = remember { FocusRequester() }
    var backButtonHasFocus by remember { mutableStateOf(false) }

    ListBackToTopHandler(listState, topFocus, topFocusHasFocus = backButtonHasFocus)

    LaunchedEffect(Unit) { runCatching { topFocus.requestFocus() } }

    LaunchedEffect(bangid, source) {
        viewModel.load(bangid, source)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clear() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            BackButton(
                onBack,
                focusRequester = topFocus,
                onFocusChanged = { backButtonHasFocus = it },
                modifier = Modifier.focusProperties { down = playAllFocus }
            )
        }

        Spacer(Modifier.height(16.dp))

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            uiState.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载失败: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = boardName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionButton(
                            icon = Icons.Rounded.PlayArrow,
                            label = "播放全部",
                            onClick = {
                                if (uiState.songs.isNotEmpty()) onSongClick(uiState.songs, 0)
                            },
                            focusRequester = playAllFocus
                        )
                        ActionButton(
                            icon = Icons.Rounded.Shuffle,
                            label = "随机播放",
                            onClick = {
                                if (uiState.songs.isNotEmpty()) onShufflePlay(uiState.songs)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${uiState.songs.size} 首歌曲",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    itemsIndexed(uiState.songs) { index, music ->
                        SongListItem(
                            music = music,
                            index = index,
                            onClick = { onSongClick(uiState.songs, index) },
                            onAddToPlaylist = { addToPlaylistViewModel.show(music) },
                            showAlbumInSubtitle = false
                        )
                    }
                }
            }
        }
    }

    AddToPlaylistHost(viewModel = addToPlaylistViewModel)
}

@Composable
private fun BackButton(
    onBack: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(120),
        label = "backScale"
    )

    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            .then(
                if (isFocused) Modifier.border(
                    3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged?.invoke(it.isFocused)
            }
            .clickable { onBack() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Text(text = "返回", fontSize = 16.sp, color = color)
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(120),
        label = "actionButtonScale"
    )
    val color = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            .then(
                if (isFocused) Modifier.border(
                    3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}
