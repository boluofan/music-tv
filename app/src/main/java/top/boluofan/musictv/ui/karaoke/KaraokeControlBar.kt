package top.boluofan.musictv.ui.karaoke

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.boluofan.musictv.ui.player.PlayerUiState
import top.boluofan.musictv.ui.player.SeekBar
import top.boluofan.musictv.ui.player.TransportButton
import top.boluofan.musictv.ui.player.formatTime
import top.boluofan.musictv.ui.theme.PlayerColors
import androidx.compose.material.icons.automirrored.rounded.QueueMusic

/**
 * K 歌控制栏：复用主播放器 [TransportButton]/[SeekBar] 的样式与布局，
 * 在"原唱/伴唱"切换与"扫码点歌"入口上做了 K 歌专属扩展。
 */
@Composable
fun KaraokeControlBar(
    uiState: PlayerUiState,
    accompanimentOn: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReSing: () -> Unit,
    onToggleAccompaniment: () -> Unit,
    onToggleQueue: () -> Unit,
    backButtonFocusRequester: FocusRequester? = null,
    playPauseFocusRequester: FocusRequester? = null,
    queueButtonFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PlayerColors.BarBackground)
            .padding(horizontal = 48.dp, vertical = 16.dp)
    ) {
        val progress = if (uiState.duration > 0) {
            uiState.currentPosition.toFloat() / uiState.duration.toFloat()
        } else 0f

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(uiState.currentPosition),
                fontSize = 12.sp,
                color = PlayerColors.TextTertiary
            )

            SeekBar(
                progress = progress,
                onSeekBy = { delta ->
                    val target = (uiState.currentPosition + delta)
                        .coerceIn(0L, uiState.duration.coerceAtLeast(0L))
                    onSeek(target)
                },
                onSeekTo = { ratio ->
                    val target = (ratio * uiState.duration.toFloat())
                        .toLong()
                        .coerceIn(0L, uiState.duration.coerceAtLeast(0L))
                    onSeek(target)
                },
                interactive = false,
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        // 进度条按 ↑：焦点跳到左上角返回按钮
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            backButtonFocusRequester?.requestFocus()
                            true
                        } else false
                    }
            )

            Text(
                text = formatTime(uiState.duration),
                fontSize = 12.sp,
                color = PlayerColors.TextTertiary
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val playIcon = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
            TransportButton(
                playIcon,
                if (uiState.isPlaying) "暂停" else "播放",
                onPlayPause,
                isLarge = true,
                focusRequester = playPauseFocusRequester
            )
            // 切歌：等价于下一首（K 歌不使用上一曲/下一曲）
            TransportButton(Icons.Rounded.SkipNext, "切歌", onNext)
            TransportButton(
                if (uiState.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                "收藏",
                onToggleFavorite
            )
            // 重唱：当前曲目从头开始（K 歌专有用，替代"重新获取歌词"）
            TransportButton(Icons.Rounded.Replay, "重唱", onReSing)
            // === 原唱 / 伴唱切换（优先双音轨，回退人声消除）===
            // 用「原 / 伴」字符作图标，直观显示当前模式，点击切换
            KaraokeTextButton(
                text = if (accompanimentOn) "伴" else "原",
                contentDescription = if (accompanimentOn) "当前伴唱，点击切原唱" else "当前原唱，点击切伴唱",
                onClick = onToggleAccompaniment
            )
            TransportButton(Icons.AutoMirrored.Rounded.QueueMusic, "播放队列", onToggleQueue, focusRequester = queueButtonFocusRequester)
        }
    }
}

/**
 * K 歌专属文字按钮：用「原 / 伴」等单字代替图标，样式与主播放器 [TransportButton] 一致。
 * 自带焦点放大/边框，支持 D-Pad 聚焦与点击。
 */
@Composable
private fun KaraokeTextButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    isLarge: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        animationSpec = tween(120),
        label = "karaokeTextBtnScale"
    )
    val size = if (isLarge) 64.dp else 48.dp
    Box(
        modifier = modifier
            .scale(scale)
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(if (isFocused) PlayerColors.ControlBgFocused else PlayerColors.ControlBg)
            .then(if (isFocused) Modifier.border(2.dp, PlayerColors.ControlBorder, RoundedCornerShape(50)) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = PlayerColors.TextPrimary,
            fontSize = if (isLarge) 26.sp else 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
