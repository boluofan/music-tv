package top.boluofan.musictv.ui.karaoke

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import top.boluofan.musictv.data.api.UrlHelper
import top.boluofan.musictv.ui.player.PlayerUiState
import top.boluofan.musictv.ui.player.TransportButton
import top.boluofan.musictv.ui.theme.PlayerColors

/**
 * 独立维护的 K 歌全屏播放界面。
 *
 * 与主播放器保持一致的视觉语言：
 * - 放大封面模糊作背景（同主播放器）
 * - 左上角返回按钮（同主播放器）
 * - 底部功能键复用主播放器 [com.songloft.tv.ui.player.TransportButton] 样式
 * - 右上角"扫码点歌"二维码（独立入口）
 */
@Composable
fun KaraokePlayerScreen(
    uiState: PlayerUiState,
    orderUrl: String?,
    accompanimentOn: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReSing: () -> Unit,
    onToggleAccompaniment: () -> Unit,
    onToggleQueue: () -> Unit,
    playPauseFocusRequester: FocusRequester? = null,
    queueButtonFocusRequester: FocusRequester? = null,
    backButtonFocusRequester: FocusRequester? = null,
    onShowControls: () -> Unit = {}
) {
    val showControls = uiState.showControls
    val backFocusRequester = backButtonFocusRequester ?: remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize().background(PlayerColors.Background)) {
        // 背景：放大封面模糊（同主播放器音乐模式）
        UrlHelper.resolve(uiState.currentSong?.img)?.let { cover ->
            AsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(60.dp),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(PlayerColors.Scrim))
        }

        // 中部：歌词 + 歌曲信息
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            // 歌曲信息（紧凑展示，不占用歌词空间）
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = uiState.currentSong?.name ?: "",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = PlayerColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = uiState.currentSong?.singer ?: "",
                    fontSize = 22.sp,
                    color = PlayerColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(16.dp))

            // 双行歌词（锚定底部，位于展开的控制栏上方，保持间距）
            KaraokeLyricsView(
                lyrics = uiState.lyrics,
                progressMs = uiState.currentPosition,
                isPlaying = uiState.isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 150.dp)
            )
        }

        // 左上角返回按钮（同主播放器）
        if (showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                TransportButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    focusRequester = backFocusRequester
                )
            }
        }

        // 右上角扫码点歌二维码
        orderUrl?.let { url ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                KaraokeQrCode(url = url)
            }
        }

        // 底部控制栏（同主播放器样式）
        if (showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                KaraokeControlBar(
                    uiState = uiState,
                    accompanimentOn = accompanimentOn,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onSeek = onSeek,
                    onSeekBy = onSeekBy,
                    onCyclePlayMode = onCyclePlayMode,
                    onToggleFavorite = onToggleFavorite,
                    onReSing = onReSing,
                    onToggleAccompaniment = onToggleAccompaniment,
                    onToggleQueue = onToggleQueue,
                    playPauseFocusRequester = playPauseFocusRequester,
                    queueButtonFocusRequester = queueButtonFocusRequester,
                    backButtonFocusRequester = backFocusRequester
                )
            }
        }

        // 控制栏隐藏时的触屏入口（同主播放器：小箭头唤起）
        AnimatedVisibility(
            visible = !showControls && !uiState.showQueueDrawer && !uiState.showSoundPanel,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(PlayerColors.TouchEntryBg)
                        .pointerInput(Unit) { detectTapGestures(onTap = { onShowControls() }) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "显示控制栏",
                    tint = PlayerColors.TouchEntryIcon,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
