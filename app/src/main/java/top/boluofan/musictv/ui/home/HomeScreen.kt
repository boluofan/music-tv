package top.boluofan.musictv.ui.home

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.ensureActive
import top.boluofan.musictv.data.model.LibraryAlbumItem
import top.boluofan.musictv.data.model.LibraryArtistItem
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.data.model.Playlist
import top.boluofan.musictv.ui.components.CoverImage
import top.boluofan.musictv.ui.components.sourceLabel
import top.boluofan.musictv.ui.components.tvFocusable
import top.boluofan.musictv.ui.navigation.DefaultFocusEffect
import top.boluofan.musictv.ui.navigation.ListBackToTopHandler
import top.boluofan.musictv.ui.navigation.RestoreFocusEffect
import top.boluofan.musictv.ui.navigation.rememberScreenFocusRestorer
import top.boluofan.musictv.ui.navigation.restorableFocus
import top.boluofan.musictv.ui.navigation.ScreenFocusRestorer

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onPlaylistClick: (Playlist) -> Unit = {},
    onSongClick: (List<MusicInfo>, Int) -> Unit = { _, _ -> },
    onArtistClick: (LibraryArtistItem, String) -> Unit = { _, _ -> },
    onAlbumClick: (LibraryAlbumItem, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val topFocus = remember { FocusRequester() }
    val firstPlaylistFocus = remember { FocusRequester() }
    var firstPlaylistComposed by remember { mutableStateOf(false) }
    val restorer = rememberScreenFocusRestorer()
    var topHasFocus by remember { mutableStateOf(false) }

    ListBackToTopHandler(
        listState = listState,
        topFocus = topFocus,
        topFocusHasFocus = topHasFocus,
        topFocusInList = false,
        jumpToTabBar = true
    )
    RestoreFocusEffect(restorer)
    DefaultFocusEffect(restorer, topFocus)

    // 焦点落到顶部刷新按钮时滚回列表顶，露出第一屏内容（我的歌单等）；
    // 焦点系统自带的 bringIntoView 滚动会取消单次 scrollToItem，故按帧重试直到到顶
    LaunchedEffect(topHasFocus) {
        if (!topHasFocus) return@LaunchedEffect
        repeat(10) {
            withFrameNanos { }
            if (!listState.canScrollBackward) return@LaunchedEffect
            runCatching { listState.scrollToItem(0) }
            ensureActive()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "首页",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
             RefreshButton(
                 refreshing = uiState.isRefreshing,
                 focusRequester = topFocus,
                 onClick = { viewModel.load() },
                 modifier = Modifier
                     .onFocusChanged { topHasFocus = it.isFocused }
                     .then(
                         if (firstPlaylistComposed) Modifier.focusProperties { down = firstPlaylistFocus }
                         else Modifier
                     )
             )
        }

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            uiState.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "重试",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .tvFocusable(cornerRadius = 8.dp, focusedFill = false, onClick = { viewModel.load() })
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }
                }
            }
            else -> {
                val displayPlaylists = remember(uiState) {
                    val lists = mutableListOf<Playlist>()
                    lists.add(Playlist(id = "default", name = "默认列表", songs = uiState.defaultSongs))
                    lists.add(Playlist(id = "love", name = "我的收藏", songs = uiState.loveSongs))
                    lists.addAll(uiState.playlists)
                    lists
                }
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        SectionTitle("我的歌单")
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            itemsIndexed(displayPlaylists) { index, playlist ->
                                val pk = "playlist:${playlist.id ?: ""}"
                                if (index == 0) {
                                    DisposableEffect(Unit) {
                                        firstPlaylistComposed = true
                                        onDispose { firstPlaylistComposed = false }
                                    }
                                }
                                PlaylistGridCard(
                                    playlist = playlist,
                                    onClick = {
                                        restorer.record(pk)
                                        onPlaylistClick(playlist)
                                    },
                                    modifier = Modifier
                                        .width(140.dp)
                                        .restorableFocus(restorer, pk)
                                        .then(if (index == 0) Modifier.focusRequester(firstPlaylistFocus) else Modifier)
                                )
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        ArtistsAlbumsRow(
                            artists = uiState.libraryArtists,
                            albums = uiState.libraryAlbums,
                            onArtistClick = { artist, source -> onArtistClick(artist, source) },
                            onAlbumClick = { album, source -> onAlbumClick(album, source) },
                            onRemoveArtist = { viewModel.removeArtist(it) },
                            onRemoveAlbum = { viewModel.removeAlbum(it) },
                            restorer = restorer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistsAlbumsRow(
    artists: List<LibraryArtistItem>,
    albums: List<LibraryAlbumItem>,
    onArtistClick: (LibraryArtistItem, String) -> Unit,
    onAlbumClick: (LibraryAlbumItem, String) -> Unit,
    onRemoveArtist: (LibraryArtistItem) -> Unit,
    onRemoveAlbum: (LibraryAlbumItem) -> Unit,
    restorer: ScreenFocusRestorer
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SectionTitle("收藏歌手")
            Spacer(Modifier.height(12.dp))
            if (artists.isEmpty()) {
                CategoryHint("暂无收藏歌手，可在歌手页收藏")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    artists.forEach { item ->
                        val pk = "home_artist:${item.id ?: ""}:${item.source ?: ""}"
                        ArtistRow(
                            item = item,
                            onClick = {
                                restorer.record(pk)
                                onArtistClick(item, item.source ?: "wy")
                            },
                            onRemove = { onRemoveArtist(item) },
                            modifier = Modifier.restorableFocus(restorer, pk)
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            SectionTitle("收藏专辑")
            Spacer(Modifier.height(12.dp))
            if (albums.isEmpty()) {
                CategoryHint("暂无收藏专辑，可在专辑页收藏")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    albums.forEach { item ->
                        val pk = "home_album:${item.id ?: ""}:${item.source ?: ""}"
                        AlbumRow(
                            item = item,
                            onClick = {
                                restorer.record(pk)
                                onAlbumClick(item, item.source ?: "wy")
                            },
                            onRemove = { onRemoveAlbum(item) },
                            modifier = Modifier.restorableFocus(restorer, pk)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun CategoryHint(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@Composable
private fun RefreshButton(
    refreshing: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocusable(cornerRadius = 18.dp, onClick = { if (!refreshing) onClick() }),
        contentAlignment = Alignment.Center
    ) {
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "刷新",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PlaylistGridCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label = "playlistGridScale"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
            .then(
                if (isFocused) Modifier.border(
                    3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CoverImage(
                url = playlist.coverUrl,
                contentDescription = playlist.name,
                placeholder = Icons.Rounded.Person,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name ?: "",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = listOfNotNull(
                playlist.count.takeIf { it > 0 }?.let { "$it 首" },
                playlist.formattedPlayCount.takeIf { it.isNotEmpty() }?.let { "播放 $it" }
            ).joinToString(" · ").ifEmpty { playlist.creatorName ?: "" },
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ArtistRow(
    item: LibraryArtistItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rowActive by remember { mutableStateOf(false) }
    var mainFocused by remember { mutableStateOf(false) }
    var removeFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scale by animateFloatAsState(
        targetValue = if (rowActive) 1.03f else 1.0f,
        animationSpec = tween(150),
        label = "homeArtistRowScale"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .focusGroup()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (rowActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
            )
            .then(
                if (rowActive) Modifier.border(
                    1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .onFocusChanged { rowActive = it.hasFocus },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .onFocusChanged { mainFocused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CoverImage(
                    url = item.avatar,
                    contentDescription = item.name,
                    placeholder = Icons.Rounded.Person,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name ?: "",
                    fontSize = 16.sp,
                    fontWeight = if (rowActive) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sourceLabel(item.source),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (mainFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .then(
                        if (mainFocused) Modifier.border(
                            3.dp, MaterialTheme.colorScheme.primary, CircleShape
                        ) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SouthEast,
                    contentDescription = "进入",
                    tint = if (mainFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(end = 14.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (removeFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else Color.Transparent
                )
                .then(
                    if (removeFocused) Modifier.border(
                        3.dp, MaterialTheme.colorScheme.primary, CircleShape
                    ) else Modifier
                )
                .onFocusChanged { removeFocused = it.isFocused }
                .clickable {
                    focusManager.moveFocus(FocusDirection.Up)
                    onRemove()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "取消收藏",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AlbumRow(
    item: LibraryAlbumItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rowActive by remember { mutableStateOf(false) }
    var mainFocused by remember { mutableStateOf(false) }
    var removeFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scale by animateFloatAsState(
        targetValue = if (rowActive) 1.03f else 1.0f,
        animationSpec = tween(150),
        label = "homeAlbumRowScale"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .focusGroup()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (rowActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
            )
            .then(
                if (rowActive) Modifier.border(
                    1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .onFocusChanged { rowActive = it.hasFocus },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .onFocusChanged { mainFocused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CoverImage(
                    url = item.img,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name ?: "",
                    fontSize = 16.sp,
                    fontWeight = if (rowActive) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        item.singer?.takeIf { it.isNotEmpty() }?.let { append(it) }
                        item.source?.takeIf { it.isNotEmpty() }?.let {
                            if (isNotEmpty()) append(" · ")
                            append(sourceLabel(it))
                        }
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (mainFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .then(
                        if (mainFocused) Modifier.border(
                            3.dp, MaterialTheme.colorScheme.primary, CircleShape
                        ) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SouthEast,
                    contentDescription = "进入",
                    tint = if (mainFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(end = 14.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (removeFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else Color.Transparent
                )
                .then(
                    if (removeFocused) Modifier.border(
                        3.dp, MaterialTheme.colorScheme.primary, CircleShape
                    ) else Modifier
                )
                .onFocusChanged { removeFocused = it.isFocused }
                .clickable {
                    focusManager.moveFocus(FocusDirection.Up)
                    onRemove()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "取消收藏",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

