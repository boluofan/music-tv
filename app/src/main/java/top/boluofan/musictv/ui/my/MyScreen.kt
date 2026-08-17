package top.boluofan.musictv.ui.my

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.nativeKeyCode
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.boluofan.musictv.data.model.LibraryAlbumItem
import top.boluofan.musictv.data.model.LibraryArtistItem
import top.boluofan.musictv.ui.components.CoverImage
import top.boluofan.musictv.ui.navigation.DefaultFocusEffect
import top.boluofan.musictv.ui.navigation.ListBackToTopHandler
import top.boluofan.musictv.ui.navigation.RestoreFocusEffect
import top.boluofan.musictv.ui.navigation.ScreenFocusRestorer
import top.boluofan.musictv.ui.navigation.rememberScreenFocusRestorer
import top.boluofan.musictv.ui.navigation.restorableFocus
import top.boluofan.musictv.ui.theme.SelectedFocusBorder

@Composable
fun MyScreen(
    viewModel: MyViewModel = hiltViewModel(),
    onArtistClick: (LibraryArtistItem, String) -> Unit = { _, _ -> },
    onAlbumClick: (LibraryAlbumItem, String) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val topFocus = remember { FocusRequester() }
    val albumTabFocus = remember { FocusRequester() }
    var topFocusHasFocus by remember { mutableStateOf(false) }
    val restorer = rememberScreenFocusRestorer()

    ListBackToTopHandler(listState, topFocus, topFocusHasFocus = topFocusHasFocus, jumpToTabBar = true)
    RestoreFocusEffect(restorer)
    DefaultFocusEffect(restorer, topFocus)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "我的",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            SettingsButton(
                onClick = {
                    restorer.record("settings")
                    onNavigateToSettings()
                },
                albumTabFocus = albumTabFocus,
                modifier = Modifier.restorableFocus(restorer, "settings")
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TabChip("收藏歌手", uiState.selectedTab == 0, focusRequester = topFocus, onFocusChange = { topFocusHasFocus = it }) { viewModel.selectTab(0) }
            TabChip("收藏专辑", uiState.selectedTab == 1, focusRequester = albumTabFocus) { viewModel.selectTab(1) }
        }

        Spacer(Modifier.height(16.dp))

        when {
            uiState.isLoading -> CenterHint("加载�?..")
            uiState.error != null -> CenterHint("加载失败�?{uiState.error}")
            uiState.selectedTab == 0 && uiState.artists.isEmpty() -> CenterHint("暂无收藏歌手，可在歌手页收藏")
            uiState.selectedTab == 1 && uiState.albums.isEmpty() -> CenterHint("暂无收藏专辑，可在专辑页收藏")
            else -> {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.selectedTab == 0) {
                itemsIndexed(uiState.artists) { _, item ->
                    val pk = "artist:${item.id ?: ""}:${item.source ?: ""}"
                    ArtistRow(
                        item = item,
                        onClick = {
                            restorer.record(pk)
                            onArtistClick(item, item.source ?: "wy")
                        },
                        onRemove = { viewModel.removeArtist(item) },
                        modifier = Modifier.restorableFocus(restorer, pk)
                    )
                }
                    } else {
                itemsIndexed(uiState.albums) { _, item ->
                    val pk = "album:${item.id ?: ""}:${item.source ?: ""}"
                    AlbumRow(
                        item = item,
                        onClick = {
                            restorer.record(pk)
                            onAlbumClick(item, item.source ?: "wy")
                        },
                        onRemove = { viewModel.removeAlbum(item) },
                        modifier = Modifier.restorableFocus(restorer, pk)
                    )
                }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CenterHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun TabChip(
    label: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(120),
        label = "myTabChipScale"
    )

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChange?.invoke(it.isFocused)
            }
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            fontSize = 15.sp,
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
private fun SettingsButton(
    onClick: () -> Unit,
    albumTabFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(120),
        label = "mySettingsScale"
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
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN ||
                        event.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT)
                ) {
                    albumTabFocus.requestFocus()
                    true
                } else false
            }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Text(text = "设置", fontSize = 16.sp, color = color)
    }
}

@Composable
private fun RowActionButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .then(
                if (focused) Modifier.border(
                    3.dp, MaterialTheme.colorScheme.primary, CircleShape
                ) else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
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
    var removeFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scale by animateFloatAsState(
        targetValue = if (rowActive) 1.03f else 1.0f,
        animationSpec = tween(150),
        label = "myArtistRowScale"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
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
        }
        RowActionButton(
            icon = Icons.Rounded.SouthEast,
            contentDescription = "进入",
            modifier = Modifier.padding(end = 10.dp),
            onClick = onClick
        )
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
    var removeFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scale by animateFloatAsState(
        targetValue = if (rowActive) 1.03f else 1.0f,
        animationSpec = tween(150),
        label = "myAlbumRowScale"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
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
        }
        RowActionButton(
            icon = Icons.Rounded.SouthEast,
            contentDescription = "进入",
            modifier = Modifier.padding(end = 10.dp),
            onClick = onClick
        )
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

private fun sourceLabel(source: String?): String = when (source) {
    "kw" -> "小蜗"
    "kg" -> "小枸"
    "tx" -> "小秋"
    "wy" -> "小芸"
    "mg" -> "小蜜"
    else -> source ?: ""
}
