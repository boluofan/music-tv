package top.boluofan.musictv.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.ui.components.ActionChip
import top.boluofan.musictv.ui.components.CoverImage
import top.boluofan.musictv.ui.components.FavoriteToggle
import top.boluofan.musictv.ui.components.SongListItem

@Composable
fun AlbumDetailScreen(
    albumId: String,
    albumName: String,
    source: String,
    singer: String? = null,
    cover: String? = null,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
    onSongClick: (List<MusicInfo>, Int) -> Unit = { _, _ -> },
    onShufflePlay: (List<MusicInfo>) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(albumId, source) {
        viewModel.load(albumId, source, albumName, singer, cover)
    }

    var backFocused by remember { mutableStateOf(false) }
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (backFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .then(
                        if (backFocused) Modifier.border(
                            3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50)
                        ) else Modifier
                    )
                    .onFocusChanged { backFocused = it.isFocused }
                    .focusRequester(backFocus)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = if (backFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "加载中...",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            uiState.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "加载失败：${uiState.error}",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 专辑头部：封面 + 名称/歌手 + 播放全部/随机播放
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            CoverImage(url = uiState.cover, contentDescription = uiState.albumName)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 20.dp)
                        ) {
                            Text(
                                text = uiState.albumName,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            uiState.singer?.takeIf { it.isNotEmpty() }?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = it,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            if (uiState.total > 0) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "共 ${uiState.total} 首",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                        if (uiState.songs.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ActionChip(icon = Icons.Rounded.PlayArrow, label = "播放全部") {
                                    onSongClick(uiState.songs, 0)
                                }
                                ActionChip(icon = Icons.Rounded.Shuffle, label = "随机播放") {
                                    onShufflePlay(uiState.songs)
                                }
                                FavoriteToggle(
                                    isFavorite = uiState.isFavorite,
                                    onClick = {
                                        viewModel.toggleFavorite(
                                            albumId = albumId,
                                            source = source,
                                            name = uiState.albumName,
                                            singer = uiState.singer,
                                            img = uiState.cover
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.songs.size) { index ->
                            SongListItem(
                                music = uiState.songs[index],
                                index = index,
                                onClick = { onSongClick(uiState.songs, index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

