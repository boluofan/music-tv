package top.boluofan.musictv.ui.search

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.draw.scale
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
import top.boluofan.musictv.data.model.SearchArtistItem
import top.boluofan.musictv.ui.components.AddToPlaylistHost
import top.boluofan.musictv.ui.components.AddToPlaylistViewModel
import top.boluofan.musictv.ui.components.CoverImage
import top.boluofan.musictv.ui.components.SongListItem
import top.boluofan.musictv.ui.navigation.DefaultFocusEffect
import top.boluofan.musictv.ui.navigation.ListBackToTopHandler
import top.boluofan.musictv.ui.navigation.RestoreFocusEffect
import top.boluofan.musictv.ui.navigation.ScreenFocusRestorer
import top.boluofan.musictv.ui.navigation.rememberScreenFocusRestorer
import top.boluofan.musictv.ui.navigation.restorableFocus
import top.boluofan.musictv.ui.theme.SelectedFocusBorder

private val SEARCH_SOURCES = listOf("kw", "kg", "tx", "wy", "mg")

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    addToPlaylistViewModel: AddToPlaylistViewModel = hiltViewModel(),
    onSongClick: (List<MusicInfo>, Int) -> Unit = { _, _ -> },
    onArtistClick: (SearchArtistItem, String) -> Unit = { _, _ -> },
    onAlbumClick: (AlbumItem, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showKeyboard by remember { mutableStateOf(false) }
    val searchBoxFocus = remember { FocusRequester() }
    val keyboardFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var searchFocused by remember { mutableStateOf(false) }
    val restorer = rememberScreenFocusRestorer()

    LaunchedEffect(showKeyboard) {
        if (showKeyboard) runCatching { keyboardFocus.requestFocus() }
    }
    LaunchedEffect(showKeyboard) {
        if (showKeyboard) return@LaunchedEffect
        runCatching { searchBoxFocus.requestFocus() }
    }

    RestoreFocusEffect(restorer)
    DefaultFocusEffect(restorer, searchBoxFocus)

    BackHandler(enabled = showKeyboard) {
        showKeyboard = false
        runCatching { searchBoxFocus.requestFocus() }
    }

    ListBackToTopHandler(
        listState = listState,
        topFocus = searchBoxFocus,
        topFocusHasFocus = searchFocused,
        jumpToTabBar = true,
        enabled = !showKeyboard
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        if (!showKeyboard) {
            Text(
                text = "搜索",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(12.dp))

            // 平台 + 类型（级联：选择的平台决定可搜索类型，反之选择类型决定可选平台）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SEARCH_SOURCES.filter { it in supportedSourcesForType(uiState.type) }) { code ->
                        FilterChip(
                            text = code.uppercase(),
                            isSelected = uiState.source == code,
                            onClick = { viewModel.selectSource(code) }
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchType.entries.filter { it in supportedTypesForSource(uiState.source) }) { type ->
                        FilterChip(
                            text = type.label,
                            isSelected = uiState.type == type,
                            onClick = { viewModel.selectType(type) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        // 搜索框
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (searchFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .then(
                        if (searchFocused) Modifier.border(
                            3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)
                        ) else Modifier
                    )
                    .focusRequester(searchBoxFocus)
                    .onFocusChanged { searchFocused = it.isFocused }
                    .clickable { showKeyboard = !showKeyboard }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.query.isEmpty()) "点击输入关键词..." else uiState.query,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (uiState.query.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            if (uiState.query.isNotEmpty()) {
                var clearFocused by remember { mutableStateOf(false) }
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "清空",
                    tint = if (clearFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (clearFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .then(
                            if (clearFocused) Modifier.border(
                                3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
                            ) else Modifier
                        )
                        .onFocusChanged { clearFocused = it.isFocused }
                        .clickable { viewModel.clearSearch() }
                        .padding(horizontal = 10.dp, vertical = 14.dp)
                )
            }
        }

        // 联想词（输入时，从服务端 tipSearch 获取，键盘打开时也展示在键盘上方）
        if (uiState.tips.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "联想词",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.tips) { tip ->
                    TagChip(tip) {
                        viewModel.onQueryChanged(tip)
                        viewModel.search()
                        showKeyboard = false
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 结果区
        Column(Modifier.weight(1f)) {
            when {
                uiState.query.isBlank() -> {
                    if (uiState.hotTags.isNotEmpty()) {
                        Text(
                            text = "热门搜索",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(uiState.hotTags) { tag ->
                                TagChip(tag) {
                                    viewModel.onQueryChanged(tag)
                                    viewModel.search()
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "输入关键词搜索",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
                uiState.isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("搜索中...", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("搜索失败：${uiState.error}", fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
                uiState.hasSearched && !hasResults(uiState) -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("未找到结果", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
                else -> when (uiState.type) {
                    SearchType.SONG -> SongResultList(
                        songs = uiState.songResults,
                        listState = listState,
                        onSongClick = { onSongClick(uiState.songResults, it) },
                        onAddToPlaylist = { music -> addToPlaylistViewModel.show(music) }
                    )
                    SearchType.SINGER -> SingerResultGrid(
                        artists = uiState.singerResults,
                        source = uiState.source,
                        listState = listState,
                        restorer = restorer,
                        onArtistClick = onArtistClick
                    )
                    SearchType.ALBUM -> AlbumResultGrid(
                        albums = uiState.albumResults,
                        source = uiState.source,
                        listState = listState,
                        restorer = restorer,
                        onAlbumClick = onAlbumClick
                    )
                }
            }
        }

        if (showKeyboard) {
            TvKeyboard(
                firstKeyFocusRequester = keyboardFocus,
                onKeyPress = { key ->
                    when (key) {
                        "←退格" -> {
                            val current = uiState.query
                            if (current.isNotEmpty()) {
                                viewModel.onQueryChanged(current.substring(0, current.length - 1))
                            }
                        }
                        "清空" -> viewModel.clearSearch()
                        "确定" -> {
                            showKeyboard = false
                            viewModel.search()
                        }
                        "空格" -> viewModel.onQueryChanged("${uiState.query} ")
                        else -> viewModel.onQueryChanged("${uiState.query}$key")
                    }
                }
            )
        }
    }

    AddToPlaylistHost(viewModel = addToPlaylistViewModel)
}

private fun hasResults(uiState: SearchUiState): Boolean = when (uiState.type) {
    SearchType.SONG -> uiState.songResults.isNotEmpty()
    SearchType.SINGER -> uiState.singerResults.isNotEmpty()
    SearchType.ALBUM -> uiState.albumResults.isNotEmpty()
}

@Composable
private fun SongResultList(
    songs: List<MusicInfo>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSongClick: (Int) -> Unit,
    onAddToPlaylist: (MusicInfo) -> Unit = {}
) {
    if (songs.isEmpty()) return
    Text(
        text = "共 ${songs.size} 首",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = 8.dp)
    )
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        itemsIndexed(songs) { index, music ->
            SongListItem(
                music = music,
                index = index,
                onClick = { onSongClick(index) },
                onAddToPlaylist = { onAddToPlaylist(music) },
                showAlbumInSubtitle = false
            )
        }
    }
}

@Composable
private fun SingerResultGrid(
    artists: List<SearchArtistItem>,
    source: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    restorer: ScreenFocusRestorer,
    onArtistClick: (SearchArtistItem, String) -> Unit
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        val rows = artists.chunked(4)
        items(rows.size) { rowIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rows[rowIndex].forEach { artist ->
                    val pk = "artist:${artist.id ?: ""}:$source"
                    ArtistCard(
                        artist = artist,
                        onClick = {
                            restorer.record(pk)
                            onArtistClick(artist, source)
                        },
                        modifier = Modifier.restorableFocus(restorer, pk).weight(1f)
                    )
                }
                repeat(4 - rows[rowIndex].size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AlbumResultGrid(
    albums: List<AlbumItem>,
    source: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    restorer: ScreenFocusRestorer,
    onAlbumClick: (AlbumItem, String) -> Unit
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        val rows = albums.chunked(4)
        items(rows.size) { rowIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rows[rowIndex].forEach { album ->
                    val pk = "album:${album.id ?: ""}:$source"
                    AlbumCard(
                        album = album,
                        onClick = {
                            restorer.record(pk)
                            onAlbumClick(album, source)
                        },
                        modifier = Modifier.restorableFocus(restorer, pk).weight(1f)
                    )
                }
                repeat(4 - rows[rowIndex].size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}


@Composable
private fun FilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .then(
                if (isFocused) Modifier.border(
                    3.dp,
                    if (isSelected) SelectedFocusBorder else MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(14.dp)
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = fg,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 4.dp)
            )
        }
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
            color = fg
        )
    }
}

@Composable
private fun TagChip(tag: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(120),
        label = "tagChipScale"
    )
    Text(
        text = tag,
        fontSize = 14.sp,
        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
        color = if (isFocused) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .then(
                if (isFocused) Modifier.border(
                    3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ArtistCard(
    artist: SearchArtistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label = "artistCardScale"
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
                url = artist.picUrl,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name ?: "",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun AlbumCard(
    album: AlbumItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label = "albumCardScale"
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
                url = album.coverUrl,
                contentDescription = album.name,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.name ?: "",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = listOfNotNull(
                album.singerName?.takeIf { it.isNotEmpty() },
                album.total?.takeIf { it.isNotEmpty() }?.let { "$it 首" }
            ).joinToString(" · "),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
