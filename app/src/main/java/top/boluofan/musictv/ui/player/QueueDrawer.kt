package top.boluofan.musictv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.boluofan.musictv.data.model.MusicInfo
import top.boluofan.musictv.ui.theme.PlayerColors

@Composable
fun QueueDrawer(
    queue: List<MusicInfo>,
    currentIndex: Int,
    onClose: () -> Unit,
    onSongClick: (Int) -> Unit,
    initialFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
    )
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
                text = "播放列表",
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
            text = "共 ${queue.size} 首",
            fontSize = 13.sp,
            color = PlayerColors.TextMuted
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(queue) { index, song ->
                val isCurrent = index == currentIndex
                var isFocused by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isCurrent -> PlayerColors.RowCurrent
                                isFocused -> PlayerColors.RowFocused
                                else -> Color.Transparent
                            }
                        )
                        .then(
                            if (isCurrent && initialFocusRequester != null)
                                Modifier.focusRequester(initialFocusRequester)
                            else Modifier
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onSongClick(index) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "正在播放",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).padding(end = 8.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.name ?: "",
                            fontSize = 14.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) PlayerColors.TextPrimary else PlayerColors.TextSecondary,
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
                }
            }
        }
    }
}
