package top.boluofan.musictv.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import top.boluofan.musictv.data.model.AlbumItem
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.ui.components.ActionChip
import top.boluofan.musictv.ui.components.AddToPlaylistHost
import top.boluofan.musictv.ui.components.AddToPlaylistViewModel
import top.boluofan.musictv.ui.components.CoverImage
import top.boluofan.musictv.ui.components.FavoriteToggle
import top.boluofan.musictv.ui.components.SongListItem
import top.boluofan.musictv.ui.navigation.DefaultFocusEffect
import top.boluofan.musictv.ui.navigation.RestoreFocusEffect
import top.boluofan.musictv.ui.navigation.ScreenFocusRestorer
import top.boluofan.musictv.ui.navigation.rememberScreenFocusRestorer
import top.boluofan.musictv.ui.navigation.restorableFocus

@Composable
fun ArtistDetailScreen(
    artistId: String,
    artistName: String,
    source: String,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
    addToPlaylistViewModel: AddToPlaylistViewModel = hiltViewModel(),
    onSongClick: (List<MusicInfo>, Int) -> Unit = { _, _ -> },
    onAlbumClick: (AlbumItem, String) -> Unit = { _, _ -> },
    onShufflePlay: (List<MusicInfo>) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val restorer = rememberScreenFocusRestorer()
    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(artistId, source) {
        viewModel.load(artistId, source)
    }

    RestoreFocusEffect(restorer)
    DefaultFocusEffect(restorer, backFocusRequester)

    var backFocused by remember { mutableStateOf(false) }

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
                    .focusRequester(backFocusRequester)
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
            Text(
                text = artistName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 歌手头部：头像 + 名称 + 描述 + 收藏
                    item {
                        val detail = uiState.detail
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(140.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                CoverImage(
                                    url = detail?.avatar,
                                    contentDescription = detail?.name ?: artistName
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 24.dp, end = 20.dp)
                            ) {
                                Text(
                                    text = detail?.name ?: artistName,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                detail?.musicSize?.takeIf { it.isNotEmpty() }?.let {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "歌曲 $it 首" + (detail.albumSize?.takeIf { s -> s.isNotEmpty() }?.let { s -> " · 专辑 $s 张" } ?: ""),
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                detail?.desc?.takeIf { it.isNotEmpty() }?.let {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = it,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
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
                                }
                                Spacer(Modifier.width(16.dp))
                            }
                            FavoriteToggle(
                                isFavorite = uiState.isFavorite,
                                onClick = {
                                    viewModel.toggleFavorite(
                                        artistId = artistId,
                                        source = source,
                                        name = detail?.name ?: artistName,
                                        avatar = detail?.avatar
                                    )
                                }
                            )
                        }
                    }

                    // 热门歌曲
                    if (uiState.songs.isNotEmpty()) {
                        item {
                            Text(
                                text = "热门歌曲",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(uiState.songs.size) { index ->
                            SongListItem(
                                music = uiState.songs[index],
                                index = index,
                                onClick = { onSongClick(uiState.songs, index) },
                                onAddToPlaylist = { addToPlaylistViewModel.show(uiState.songs[index]) }
                            )
                        }
                    }

                    // 专辑
                    if (uiState.albums.isNotEmpty()) {
                        item {
                            Text(
                                text = "专辑",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        item {
                            AlbumGrid(
                                albums = uiState.albums,
                                restorer = restorer,
                                onAlbumClick = { onAlbumClick(it, source) }
                            )
                        }
                    }
                }
            }
        }
    }

    AddToPlaylistHost(viewModel = addToPlaylistViewModel)
}

@Composable
private fun AlbumGrid(
    albums: List<AlbumItem>,
    restorer: ScreenFocusRestorer,
    onAlbumClick: (AlbumItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(albums) { album ->
            val pk = "album:${album.id ?: ""}"
            AlbumCard(
                album = album,
                onClick = {
                    restorer.record(pk)
                    onAlbumClick(album)
                },
                modifier = Modifier.restorableFocus(restorer, pk)
            )
        }
    }
}

@Composable
private fun AlbumCard(album: AlbumItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .focusGroup()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .then(
                if (isFocused) Modifier.border(
                    3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
        ) {
            CoverImage(url = album.coverUrl, contentDescription = album.name)
        }
        Text(
            text = album.name ?: "",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = buildString {
                album.singerName?.takeIf { it.isNotEmpty() }?.let { append(it) }
                album.total?.takeIf { it.isNotEmpty() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append("$it 首")
                }
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

