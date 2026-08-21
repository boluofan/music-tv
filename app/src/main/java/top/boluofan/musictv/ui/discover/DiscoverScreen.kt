package top.boluofan.musictv.ui.discover

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.ensureActive
import top.boluofan.musictv.data.model.BoardItem
import top.boluofan.musictv.data.model.Playlist
import top.boluofan.musictv.ui.components.CoverImage
import top.boluofan.musictv.ui.components.sourceLabel
import top.boluofan.musictv.ui.components.tvFocusable
import top.boluofan.musictv.ui.navigation.DefaultFocusEffect
import top.boluofan.musictv.ui.navigation.ListBackToTopHandler
import top.boluofan.musictv.ui.navigation.RestoreFocusEffect
import top.boluofan.musictv.ui.navigation.ScreenFocusRestorer
import top.boluofan.musictv.ui.navigation.rememberScreenFocusRestorer
import top.boluofan.musictv.ui.navigation.restorableFocus
import top.boluofan.musictv.ui.theme.SelectedFocusBorder

private val SOURCES = listOf("tx", "kw", "kg", "wy", "mg").map { it to sourceLabel(it) }

@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel = hiltViewModel(),
    onPlaylistClick: (Playlist) -> Unit = {},
    onBoardClick: (BoardItem, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val topFocus = remember { FocusRequester() }
    val sourceFocus = remember { FocusRequester() }
    var topFocusHasFocus by remember { mutableStateOf(false) }
    val restorer = rememberScreenFocusRestorer()

    // 滚动接近底部时懒加载下一页
    LaunchedEffect(uiState.section, uiState.selectedTagId, uiState.source) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to info.totalItemsCount
        }.collect { (lastVisible, totalItems) ->
            if (totalItems > 0 && lastVisible >= totalItems - 3) {
                viewModel.loadMore()
            }
        }
    }

    // 切换平台/分区/标签后回到第一屏（加载中列表被移出组合，加载完成后再滚动，按帧重试防被焦点滚动取消）
    LaunchedEffect(uiState.source, uiState.section, uiState.selectedTagId, uiState.isLoading) {
        if (uiState.isLoading) return@LaunchedEffect
        repeat(10) {
            withFrameNanos { }
            if (!listState.canScrollBackward) return@LaunchedEffect
            runCatching { listState.scrollToItem(0) }
            ensureActive()
        }
    }

    ListBackToTopHandler(
        listState = listState,
        topFocus = topFocus,
        topFocusHasFocus = topFocusHasFocus,
        topFocusInList = false,
        jumpToTabBar = true
    )

    RestoreFocusEffect(restorer)
    DefaultFocusEffect(restorer, topFocus)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "发现",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionChip(
                    label = DiscoverSection.SQUARE.label,
                    isSelected = uiState.section == DiscoverSection.SQUARE,
                    focusRequester = topFocus,
                    leftFocus = sourceFocus,
                    onFocusChange = { topFocusHasFocus = it }
                ) { viewModel.switchSection(DiscoverSection.SQUARE) }
                SectionChip(
                    label = DiscoverSection.LEADERBOARD.label,
                    isSelected = uiState.section == DiscoverSection.LEADERBOARD
                ) { viewModel.switchSection(DiscoverSection.LEADERBOARD) }
                RefreshButton { viewModel.refresh() }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.weight(1f)) {
            val sourceTopPadding = if (uiState.section == DiscoverSection.SQUARE) {
                64.dp
            } else {
                12.dp
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(88.dp).padding(top = sourceTopPadding)
            ) {
                SOURCES.forEach { (code, name) ->
                    SourceChip(
                        label = name,
                        isSelected = uiState.source == code,
                        focusRequester = if (uiState.source == code) sourceFocus else null,
                        onClick = { viewModel.selectSource(code) }
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            when {
                uiState.isLoading -> {
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载中...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                uiState.error != null -> {
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("加载失败: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    when (uiState.section) {
                        DiscoverSection.SQUARE -> SquareContent(
                            uiState = uiState,
                            listState = listState,
                            restorer = restorer,
                            onTagClick = { viewModel.selectTag(it) },
                            onPlaylistClick = onPlaylistClick
                        )
                        DiscoverSection.LEADERBOARD -> LeaderboardContent(
                            uiState = uiState,
                            listState = listState,
                            restorer = restorer,
                            onBoardClick = { board -> onBoardClick(board, uiState.source) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SquareContent(
    uiState: DiscoverUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    restorer: ScreenFocusRestorer,
    onTagClick: (String?) -> Unit,
    onPlaylistClick: (Playlist) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            item {
                TagChip("全部", isSelected = uiState.selectedTagId == null) { onTagClick(null) }
            }
            items(uiState.squareTags.size) { i ->
                val tag = uiState.squareTags[i]
                TagChip(tag.name ?: "", isSelected = uiState.selectedTagId == tag.id) {
                    onTagClick(tag.id)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = maxOf(1, ((maxWidth + 12.dp) / (140.dp + 12.dp)).toInt())
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
            ) {
                val rows = uiState.playlists.chunked(columns)
                items(rows.size) { rowIndex ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows[rowIndex].forEach { playlist ->
                            val pk = "playlist:${playlist.id ?: ""}"
                            PlaylistCard(
                                playlist = playlist,
                                onClick = {
                                    restorer.record(pk)
                                    onPlaylistClick(playlist)
                                },
                                modifier = Modifier.restorableFocus(restorer, pk).width(140.dp)
                            )
                        }
                    }
                }
                if (uiState.isLoadingMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardContent(
    uiState: DiscoverUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    restorer: ScreenFocusRestorer,
    onBoardClick: (BoardItem) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = maxOf(1, ((maxWidth + 12.dp) / (140.dp + 12.dp)).toInt())
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            val rows = uiState.boards.chunked(columns)
            items(rows.size) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows[rowIndex].forEach { board ->
                        val pk = "board:${board.bangid ?: board.id ?: ""}"
                        BoardCard(
                            board = board,
                            onClick = {
                                restorer.record(pk)
                                onBoardClick(board)
                            },
                            modifier = Modifier.restorableFocus(restorer, pk).width(140.dp)
                        )
                    }
                }
            }
            if (uiState.boards.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "该平台暂无榜单",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionChip(
    label: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    leftFocus: FocusRequester? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(120),
        label = "sectionChipScale"
    )

    Text(
        text = if (isSelected) "✓ $label" else label,
        fontSize = 14.sp,
        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
        color = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isFocused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        },
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .then(
                if (isFocused) Modifier.border(
                    3.dp,
                    if (isSelected) SelectedFocusBorder else MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (leftFocus != null) Modifier.focusProperties { left = leftFocus } else Modifier)
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChange?.invoke(it.isFocused)
            }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun RefreshButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .tvFocusable(cornerRadius = 18.dp, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "刷新",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SourceChip(
    label: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
            )
            .then(
                if (isFocused) Modifier.border(
                    3.dp,
                    if (isSelected) SelectedFocusBorder else MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(10.dp)
                ) else Modifier
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSelected) "✓ $label" else label,
            fontSize = 14.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                isFocused -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            }
        )
    }
}

@Composable
private fun TagChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
        color = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isFocused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        },
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .then(
                if (isFocused) Modifier.border(
                    3.dp,
                    if (isSelected) SelectedFocusBorder else MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(14.dp)
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label = "playlistCardScale"
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
private fun BoardCard(
    board: BoardItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label = "boardCardScale"
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
                .background(
                    if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = board.name ?: "",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
