package top.boluofan.musictv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.ui.navigation.LocalPlayerBarBridge

enum class SongItemFavoriteMode {
    NONE,
    TOGGLE,
    REMOVE
}

@Composable
fun SongListItem(
    music: MusicInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    favoriteMode: SongItemFavoriteMode = SongItemFavoriteMode.NONE,
    isFavorite: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    onAddToPlaylist: (() -> Unit)? = null,
    showAlbumInSubtitle: Boolean = true
) {
    var rowActive by remember { mutableStateOf(false) }
    var mainFocused by remember { mutableStateOf(false) }
    var favFocused by remember { mutableStateOf(false) }
    var addFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val playerBridge = LocalPlayerBarBridge.current
    val scale by animateFloatAsState(
        targetValue = if (rowActive) 1.03f else 1.0f,
        animationSpec = tween(150),
        label = "songItemScale"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .focusGroup()
            .onFocusChanged { rowActive = it.hasFocus }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (rowActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
            )
            .then(
                if (rowActive) Modifier.border(
                    1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)
                ) else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .onFocusChanged { mainFocused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (index != null) {
                Text(
                    text = "${index + 1}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.width(32.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = music.name ?: "",
                    fontSize = 16.sp,
                    fontWeight = if (rowActive) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = buildString {
                    music.singer?.takeIf { it.isNotEmpty() }?.let { append(it) }
                    if (showAlbumInSubtitle) {
                        music.albumName?.takeIf { it.isNotEmpty() }?.let {
                            if (isNotEmpty()) append(" · ")
                            append(it)
                        }
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
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
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "播放",
                    tint = if (mainFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (onAddToPlaylist != null) {
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (addFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .then(
                        if (addFocused) Modifier.border(
                            3.dp, MaterialTheme.colorScheme.primary, CircleShape
                        ) else Modifier
                    )
                    .onFocusChanged { addFocused = it.isFocused }
                    .then(
                        if (playerBridge != null) Modifier.onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                            ) playerBridge.requestPlayerFocus() else false
                        } else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onAddToPlaylist() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "加歌到歌单",
                    tint = if (addFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (favoriteMode != SongItemFavoriteMode.NONE) {
            val filled = favoriteMode == SongItemFavoriteMode.REMOVE || isFavorite
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (favFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .then(
                        if (favFocused) Modifier.border(
                            3.dp, MaterialTheme.colorScheme.primary, CircleShape
                        ) else Modifier
                    )
                    .onFocusChanged { favFocused = it.isFocused }
                    .then(
                        if (playerBridge != null) Modifier.onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                            ) playerBridge.requestPlayerFocus() else false
                        } else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (favoriteMode == SongItemFavoriteMode.REMOVE) {
                            focusManager.moveFocus(FocusDirection.Up)
                        }
                        onFavoriteClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (filled) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (filled) "取消收藏" else "收藏",
                    tint = when {
                        favFocused -> MaterialTheme.colorScheme.primary
                        filled -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
