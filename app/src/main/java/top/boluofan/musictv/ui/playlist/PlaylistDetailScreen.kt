package top.boluofan.musictv.ui.playlist

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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.ui.components.AddToPlaylistHost
import top.boluofan.musictv.ui.components.AddToPlaylistViewModel
import top.boluofan.musictv.ui.components.CoverImage
import top.boluofan.musictv.ui.components.SongItemFavoriteMode
import top.boluofan.musictv.ui.components.SongListItem
import top.boluofan.musictv.ui.components.tvFocusable
import top.boluofan.musictv.ui.navigation.ListBackToTopHandler
import top.boluofan.musictv.ui.search.TvKeyboard

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    source: String?,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
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
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    ListBackToTopHandler(listState, topFocus, topFocusHasFocus = backButtonHasFocus)

    LaunchedEffect(Unit) { runCatching { topFocus.requestFocus() } }

    LaunchedEffect(playlistId, source) {
        viewModel.load(playlistId, source)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clear() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
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
                val playlist = uiState.playlist
                if (playlist != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            CoverImage(
                                url = playlist.coverUrl,
                                contentDescription = playlist.name,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(Modifier.width(20.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name ?: "",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${uiState.songs.size} 首歌曲",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            playlist.descText?.takeIf { it.isNotEmpty() }?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = it,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.width(340.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ActionButton(
                                    icon = Icons.Rounded.PlayArrow,
                                    label = "播放全部",
                                    onClick = {
                                        if (uiState.songs.isNotEmpty()) onSongClick(uiState.songs, 0)
                                    },
                                    focusRequester = playAllFocus,
                                    modifier = Modifier.weight(1f)
                                )
                                ActionButton(
                                    icon = Icons.Rounded.Shuffle,
                                    label = "随机播放",
                                    onClick = {
                                        if (uiState.songs.isNotEmpty()) onShufflePlay(uiState.songs)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (uiState.isUserList) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ActionButton(
                                        icon = Icons.Rounded.Edit,
                                        label = "重命名",
                                        onClick = { showRenameDialog = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionButton(
                                        icon = Icons.Rounded.Delete,
                                        label = "删除歌单",
                                        onClick = { showDeleteDialog = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        itemsIndexed(uiState.songs) { index, music ->
                            if (source == null) {
                                // 我的歌单：右侧为移除按钮
                                SongListItem(
                                    music = music,
                                    index = index,
                                    onClick = { onSongClick(uiState.songs, index) },
                                    favoriteMode = SongItemFavoriteMode.REMOVE,
                                    onFavoriteClick = { viewModel.removeSong(music.songId) },
                                    showAlbumInSubtitle = false
                                )
                            } else {
                                // 服务器歌单：右侧为加歌到我的歌单按钮
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
        }
    }

    AddToPlaylistHost(viewModel = addToPlaylistViewModel)

    if (showRenameDialog) {
        RenamePlaylistDialog(
            initialName = uiState.playlist?.name.orEmpty(),
            onConfirm = { newName ->
                showRenameDialog = false
                viewModel.renamePlaylist(newName)
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "删除歌单「${uiState.playlist?.name.orEmpty()}」？",
            message = "歌单中的歌曲不会被删除",
            confirmText = "删除",
            onConfirm = {
                showDeleteDialog = false
                viewModel.deletePlaylist { success -> if (success) onBack() }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun RenamePlaylistDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val firstKeyFocus = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
        ) {
            Text(
                text = "重命名歌单",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = if (name.isEmpty()) "输入新名称..." else name,
                fontSize = 18.sp,
                color = if (name.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
            Spacer(Modifier.height(12.dp))
            TvKeyboard(
                firstKeyFocusRequester = firstKeyFocus,
                onKeyPress = { key ->
                    when (key) {
                        "←退格" -> name = name.dropLast(1)
                        "清空" -> name = ""
                        "确定" -> {
                            if (name.isNotBlank()) onConfirm(name)
                            else onDismiss()
                        }
                        "空格" -> name += " "
                        else -> name += key
                    }
                }
            )
        }
    }

    LaunchedEffect(Unit) { runCatching { firstKeyFocus.requestFocus() } }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cancelFocus = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(24.dp))
            Row {
                DialogButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(cancelFocus)
                )
                Spacer(Modifier.width(16.dp))
                DialogButton(
                    text = confirmText,
                    onClick = onConfirm
                )
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
}

@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .tvFocusable(cornerRadius = 8.dp, onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 28.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
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
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(120),
        label = "actionButtonScale"
    )
    val color = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
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
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
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
