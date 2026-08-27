package top.boluofan.musictv.ui.karaoke

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.ui.theme.PlayerColors

/**
 * K 歌独立歌曲列表（与主页播放队列隔离）。
 *
 * - 当前演唱中的歌曲固定置顶展示，不可置顶/删除；
 * - 其余未唱歌曲均可通过「置顶」移动到下一首演唱，或通过「删除」移出列表。
 */
@Composable
fun KaraokeQueueList(
    queue: List<MusicInfo>,
    currentIndex: Int,
    onClose: () -> Unit,
    onSongClick: (Int) -> Unit,
    onMoveTop: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    initialFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var pendingRemoveIndex by remember { mutableStateOf<Int?>(null) }
    Column(
        modifier = modifier
            .background(PlayerColors.QueueBackground)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "K 歌歌单",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PlayerColors.TextPrimary
            )
            var closeFocused by remember { mutableStateOf(false) }
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "关闭",
                tint = if (closeFocused) MaterialTheme.colorScheme.onPrimary else PlayerColors.TextTertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (closeFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else Color.Transparent
                    )
                    .onFocusChanged { closeFocused = it.isFocused }
                    .clickable { onClose() }
                    .padding(8.dp)
            )
        }

        Text(
            text = "共 ${queue.size} 首（唱过自动移除）",
            fontSize = 13.sp,
            color = PlayerColors.TextMuted
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 当前演唱中：固定置顶，无操作按钮
            val current = queue.getOrNull(currentIndex)
            if (current != null) {
                item(key = "current") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PlayerColors.RowCurrent)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "正在演唱",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).padding(end = 8.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "正在演唱 · ${current.name ?: ""}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PlayerColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (current.singer != null) {
                                Text(
                                    current.singer,
                                    fontSize = 12.sp,
                                    color = PlayerColors.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 其余未唱歌曲（保留原索引，用于操作的 index 参数）
            itemsIndexed(
                items = queue.mapIndexed { i, s -> i to s }.filter { it.first != currentIndex },
                key = { _, (i, s) -> "k$i${s.songId}" }
            ) { _, (index, song) ->
                KaraokeQueueRow(
                    song = song,
                    onSongClick = { onSongClick(index) },
                    onMoveTop = { onMoveTop(index) },
                    onRemove = { pendingRemoveIndex = index },
                    initialFocusRequester = if (index == currentIndex + 1) initialFocusRequester else null
                )
            }
        }

        // 删除确认弹窗：避免误删 K 歌歌单中的歌曲（焦点锁定在弹窗内，不会跳出主界面）
        pendingRemoveIndex?.let { idx ->
            KaraokeDeleteDialog(
                songTitle = queue.getOrNull(idx)?.name,
                onConfirm = {
                    onRemove(idx)
                    pendingRemoveIndex = null
                },
                onDismiss = { pendingRemoveIndex = null }
            )
        }
    }
}

@Composable
private fun KaraokeQueueRow(
    song: MusicInfo,
    onSongClick: () -> Unit,
    onMoveTop: () -> Unit,
    onRemove: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    var rowFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (rowFocused) PlayerColors.RowFocused else Color.Transparent)
            // 行本身不抢占焦点：聚焦态由子按钮聚合；歌曲名不可被焦点选中
            .onFocusChanged { rowFocused = it.hasFocus }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 歌曲主区域：不可聚焦，仅触摸点击可演唱该曲；D-Pad 焦点直接落在右侧按钮
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(Unit) { detectTapGestures { onSongClick() } }
                .padding(end = 8.dp)
        ) {
            Text(
                song.name ?: "", fontSize = 14.sp,
                fontWeight = if (rowFocused) FontWeight.Bold else FontWeight.Normal,
                color = if (rowFocused) PlayerColors.TextPrimary else PlayerColors.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (song.singer != null) {
                Text(
                    song.singer, fontSize = 12.sp,
                    color = PlayerColors.TextMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 置顶：移动到下一首演唱（独立可聚焦，初始焦点落在此）
        KaraokeListIconButton(
            imageVector = Icons.Rounded.KeyboardArrowUp,
            contentDescription = "置顶",
            onClick = onMoveTop,
            focusRequester = initialFocusRequester
        )
        // 删除：移出 K 歌列表（独立可聚焦按钮）
        KaraokeListIconButton(
            imageVector = Icons.Rounded.Delete,
            contentDescription = "删除",
            onClick = onRemove
        )
    }
}

@Composable
private fun KaraokeDeleteDialog(
    songTitle: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PlayerColors.QueueBackground)
                .padding(28.dp)
                .onPreviewKeyEvent { event ->
                    // 返回键关闭弹窗，并消费事件避免焦点跳回主界面
                    if (event.type == KeyEventType.KeyDown
                        && event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK
                    ) {
                        onDismiss()
                        true
                    } else false
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "删除歌曲",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PlayerColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "确定将《${songTitle ?: ""}》从 K 歌歌单中删除吗？",
                fontSize = 15.sp,
                color = PlayerColors.TextSecondary
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KaraokeDialogButton("取消", onDismiss)
                KaraokeDialogButton("删除", onConfirm, confirmFocus, isPrimary = true)
            }
        }
    }
    // 默认焦点落在「删除」按钮，弹窗作为独立窗口承载焦点，上下左右不会跳出到主界面
    LaunchedEffect(Unit) { runCatching { confirmFocus.requestFocus() } }
}

@Composable
private fun KaraokeDialogButton(
    text: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    isPrimary: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = if (isPrimary) MaterialTheme.colorScheme.primary else PlayerColors.TextPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .then(
                if (isFocused) Modifier.border(
                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 24.dp, vertical = 10.dp)
    )
}

@Composable
private fun KaraokeListIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = if (isFocused) MaterialTheme.colorScheme.primary else PlayerColors.TextTertiary,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else Color.Transparent
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .focusable()
            .padding(8.dp)
    )
}
