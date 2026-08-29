package top.boluofan.musictv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import top.boluofan.musictv.data.api.UrlHelper
import top.boluofan.musictv.domain.KeyMappingManager
import top.boluofan.musictv.ui.components.CoverImage
import top.boluofan.musictv.ui.components.tvFocusable
import top.boluofan.musictv.ui.karaoke.KaraokePlayerScreen
import top.boluofan.musictv.ui.karaoke.KaraokeQueueList
import top.boluofan.musictv.ui.player.ControlBar
import top.boluofan.musictv.ui.player.LyricsPanel
import top.boluofan.musictv.ui.player.SoundPanel
import top.boluofan.musictv.ui.player.PlayerViewModel
import top.boluofan.musictv.ui.player.QueueDrawer
import top.boluofan.musictv.ui.player.TransportButton
import top.boluofan.musictv.ui.theme.PlayerColors
import top.boluofan.musictv.ui.theme.TvTheme
import kotlinx.coroutines.delay
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    @Inject
    lateinit var keyMappingManager: KeyMappingManager

    /** 用户自定义按键映射：命中映射表的 keycode 翻译成标准功能键后继续分发 */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        super.dispatchKeyEvent(keyMappingManager.translateEvent(event))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TvTheme {
                PlayerScreen(
                    viewModel = hiltViewModel(),
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    var interactionCount by remember { mutableIntStateOf(0) }
    val controlBarFocus = remember { FocusRequester() }
    val queueDrawerFocus = remember { FocusRequester() }
    val soundPanelFocus = remember { FocusRequester() }
    val soundButtonFocus = remember { FocusRequester() }
    // K 歌模式焦点：进入时聚焦到 K 歌控制栏的播放/暂停键，退出时聚焦到主控制栏的麦克风按钮
    val karaokePlayPauseFocus = remember { FocusRequester() }
    val karaokeQueueButtonFocus = remember { FocusRequester() }
    val karaokeQueueListFocus = remember { FocusRequester() }
    val micButtonFocus = remember { FocusRequester() }

    // K 歌模式下的"播放队列"弹窗状态
    var showKaraokeQueue by remember { mutableStateOf(false) }
    var karaokeQueueWasOpen by remember { mutableStateOf(false) }
    // K 歌歌单刚从打开→关闭的瞬间标记，主焦点 effect 据此跳过 karaoke 播放/暂停分支，让出焦点
    var karaokeQueueJustClosed by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showKaraokeQueue -> showKaraokeQueue = false
            uiState.showExitKaraokeConfirm -> viewModel.dismissExitKaraokeConfirm()
            uiState.showQueueDrawer -> viewModel.closeQueueDrawer()
            uiState.showSoundPanel -> viewModel.closeSoundPanel()
            uiState.karaokeModeEnabled -> viewModel.requestExitKaraoke()
            uiState.showControls -> viewModel.hideControls()
            else -> onBack()
        }
    }

    LaunchedEffect(uiState.isPlaying, uiState.showControls, uiState.showSoundPanel, interactionCount) {
        if (uiState.isPlaying && uiState.showSoundPanel) {
            delay(10_000)
            viewModel.closeSoundPanel()
        } else if (uiState.isPlaying && uiState.showControls) {
            delay(10_000)
            viewModel.hideControls()
        }
    }

    LaunchedEffect(uiState.showControls, uiState.showQueueDrawer, uiState.showSoundPanel, uiState.karaokeModeEnabled, showKaraokeQueue, uiState.showExitKaraokeConfirm) {
        // 等待 AnimatedVisibility 完成组合后再请求焦点
        delay(100)
        runCatching {
            when {
                // 退出确认弹窗打开时焦点交给弹窗自身，关闭时再走下方分支恢复
                uiState.showExitKaraokeConfirm -> Unit
                showKaraokeQueue -> karaokeQueueListFocus.requestFocus()
                uiState.showQueueDrawer -> queueDrawerFocus.requestFocus()
                uiState.showSoundPanel -> soundPanelFocus.requestFocus()
                uiState.karaokeModeEnabled && !karaokeQueueJustClosed -> karaokePlayPauseFocus.requestFocus()
                uiState.showControls -> controlBarFocus.requestFocus()
            }
        }
    }

    // 音效面板关闭后，焦点回到控制栏音效按钮（不可见时兜底到播放/暂停）
    var soundPanelWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.showSoundPanel) {
        if (soundPanelWasOpen && !uiState.showSoundPanel) {
            runCatching { soundButtonFocus.requestFocus() }
        }
        soundPanelWasOpen = uiState.showSoundPanel
    }

    var didSeekDuringPress by remember { mutableStateOf(false) }
    val seekStepMs = 10_000L

    // K 歌歌单弹窗关闭后，焦点回到 K 歌控制栏的"歌单"按钮
    LaunchedEffect(showKaraokeQueue) {
        if (showKaraokeQueue) {
            karaokeQueueJustClosed = false
        } else if (karaokeQueueWasOpen) {
            karaokeQueueJustClosed = true
            delay(150)
            runCatching { karaokeQueueButtonFocus.requestFocus() }
            // 焦点已交回歌单按钮，立即复位标志，避免后续焦点恢复被永久跳过
            karaokeQueueJustClosed = false
        }
        karaokeQueueWasOpen = showKaraokeQueue
    }

    // 退出 K 歌模式后，焦点回到主播放器控制栏的"麦克风（K 歌入口）"按钮
    var wasKaraokeMode by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.karaokeModeEnabled) {
        if (wasKaraokeMode && !uiState.karaokeModeEnabled) {
            delay(120)
            runCatching { micButtonFocus.requestFocus() }
        }
        wasKaraokeMode = uiState.karaokeModeEnabled
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerColors.Background)
            .pointerInput(uiState.showControls, uiState.showQueueDrawer, uiState.showSoundPanel) {
                // 控制栏/抽屉/音效面板弹出时点击其他区域关闭；子节点消费的事件不会触发此回调
                when {
                    uiState.showQueueDrawer -> detectTapGestures { viewModel.closeQueueDrawer() }
                    uiState.showSoundPanel -> detectTapGestures { viewModel.closeSoundPanel() }
                    uiState.showControls -> detectTapGestures { viewModel.hideControls() }
                }
            }
            .onPreviewKeyEvent { event ->
                val controlsHidden = !uiState.showControls && !uiState.showQueueDrawer
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        interactionCount++
                        when (event.key) {
                            // 优先于焦点链处理，保证抽屉/控制栏一次返回即关闭
                            Key.Back -> {
                                when {
                                    showKaraokeQueue -> {
                                        showKaraokeQueue = false
                                        true
                                    }
                                    uiState.showQueueDrawer -> {
                                        viewModel.closeQueueDrawer()
                                        true
                                    }
                                    uiState.showSoundPanel -> {
                                        viewModel.closeSoundPanel()
                                        true
                                    }
                                    uiState.showControls -> {
                                        viewModel.hideControls()
                                        true
                                    }
                                    else -> false
                                }
                            }
                            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                                viewModel.togglePlay(); true
                            }
                            Key.MediaNext, Key.MediaSkipForward -> {
                                viewModel.nextTrack(); true
                            }
                            Key.MediaPrevious, Key.MediaSkipBackward -> {
                                viewModel.previousTrack(); true
                            }
                            Key.DirectionLeft, Key.DirectionRight -> {
                                if (controlsHidden) {
                                    if (event.nativeKeyEvent.repeatCount > 0) {
                                        didSeekDuringPress = true
                                        viewModel.seekBy(
                                            if (event.key == Key.DirectionLeft) -seekStepMs else seekStepMs
                                        )
                                    }
                                    true
                                } else false
                            }
                            Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter -> {
                                if (controlsHidden) {
                                    viewModel.showControls()
                                    true
                                } else false
                            }
                            else -> false
                        }
                    }
                    KeyEventType.KeyUp -> {
                        when (event.key) {
                            Key.DirectionLeft, Key.DirectionRight -> {
                                if (controlsHidden) {
                                    if (!didSeekDuringPress) {
                                        if (event.key == Key.DirectionLeft) viewModel.previousTrack()
                                        else viewModel.nextTrack()
                                    }
                                    didSeekDuringPress = false
                                    true
                                } else false
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        // K 歌模式分支（PR1:替换主播放 UI;QueueList 弹窗独立绘制）
        if (uiState.karaokeModeEnabled) {
            KaraokePlayerScreen(
                uiState = uiState,
                orderUrl = uiState.karaokeOrderUrl,
                accompanimentOn = uiState.isAccompanimentOn,
                onBack = { viewModel.requestExitKaraoke() },
                onPlayPause = { viewModel.togglePlay() },
                onNext = { viewModel.nextTrack() },
                onSeek = { viewModel.seekTo(it) },
                onSeekBy = { viewModel.seekBy(it) },
                onCyclePlayMode = { viewModel.cyclePlayMode() },
                onToggleFavorite = { viewModel.toggleFavorite() },
                onReSing = {
                    viewModel.seekTo(0L)
                    viewModel.togglePlay()
                },
                onToggleAccompaniment = { viewModel.toggleAccompaniment() },
                onToggleQueue = { showKaraokeQueue = !showKaraokeQueue },
                playPauseFocusRequester = karaokePlayPauseFocus,
                queueButtonFocusRequester = karaokeQueueButtonFocus,
                backButtonFocusRequester = micButtonFocus,
                onShowControls = { viewModel.showControls() }
            )
            if (showKaraokeQueue) {
                KaraokeQueueList(
                    queue = uiState.karaokeList,
                    currentIndex = uiState.currentIndex,
                    onClose = { showKaraokeQueue = false },
                    onSongClick = { viewModel.karaokePlayAt(it) },
                    onMoveTop = { viewModel.karaokeMoveTop(it) },
                    onRemove = { viewModel.karaokeRemove(it) },
                    initialFocusRequester = karaokeQueueListFocus,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(400.dp)
                        .fillMaxHeight()
                )
            }
        } else if (uiState.currentSong != null) {
            UrlHelper.resolve(uiState.currentSong?.picUrl)?.let { cover ->
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(60.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PlayerColors.Scrim)
                )
            }
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .sizeIn(maxWidth = 300.dp, maxHeight = 300.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(54.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            CoverImage(
                                url = uiState.currentSong?.picUrl,
                                contentDescription = uiState.currentSong?.name,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = uiState.currentSong?.name ?: "",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = PlayerColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = uiState.currentSong?.singer ?: "",
                            fontSize = 14.sp,
                            color = PlayerColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LyricsPanel(
                        lyrics = uiState.lyrics,
                        currentIndex = uiState.currentLyricIndex,
                        currentPosition = uiState.currentPosition,
                        highlightColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("未选择歌曲", color = PlayerColors.TextTertiary, fontSize = 20.sp)
            }
        }

        // 左上角返回按钮：与控制栏同显同隐（10s 无操作自动隐藏、点击空白/返回键先关控制栏）
        // K 歌模式下不显示返回按钮（已内置在 K 歌控制栏）
        if (!uiState.karaokeModeEnabled) {
            AnimatedVisibility(
                visible = uiState.showControls,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                TransportButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack
                )
            }
        }

        // 主控制栏 + 触屏小箭头：K 歌模式下不显示（K 歌有独立的 KaraokeControlBar）
        if (!uiState.karaokeModeEnabled) {
            AnimatedVisibility(
                visible = uiState.showControls,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .pointerInput(Unit) { detectTapGestures { } } // 消费控制栏区域点击，避免触发外部关闭
            ) {
                ControlBar(
                    uiState = uiState,
                    onPlayPause = { viewModel.togglePlay() },
                    onNext = { viewModel.nextTrack() },
                    onPrevious = { viewModel.previousTrack() },
                    onSeek = { viewModel.seekTo(it) },
                    onCyclePlayMode = { viewModel.cyclePlayMode() },
                    onToggleQueue = { viewModel.toggleQueueDrawer() },
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    onRefreshLyrics = { viewModel.refreshLyrics() },
                    onToggleSound = if (uiState.eqEnabled || uiState.sfxEnabled) ({ viewModel.toggleSoundPanel() }) else null,
                    onEnterKaraokeMode = { viewModel.enterKaraokeMode() },
                    soundButtonFocusRequester = soundButtonFocus,
                    micButtonFocusRequester = micButtonFocus,
                    isLyricRefreshing = uiState.isLyricRefreshing,
                    playPauseFocusRequester = controlBarFocus,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(
                visible = !uiState.showControls && !uiState.showQueueDrawer && !uiState.showSoundPanel,
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
                        .pointerInput(Unit) { detectTapGestures { viewModel.showControls() } },
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

        AnimatedVisibility(
            visible = uiState.showSoundPanel,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SoundPanel(
                sfxSupported = uiState.sfxSupported,
                sfxOnA2dp = uiState.sfxOnA2dp,
                sfxMode = uiState.sfxMode,
                sfxStrength = uiState.sfxStrength,
                sfxModeKeys = uiState.sfxModeKeys,
                sfxModeNames = uiState.sfxModeNames,
                sfxModeSupported = uiState.sfxModeSupported,
                onSetSfxMode = { viewModel.setSfxMode(it) },
                onSetSfxStrength = { viewModel.setSfxStrength(it) },
                eqSupported = uiState.eqSupported,
                eqEnabled = uiState.eqEnabled,
                eqPreset = uiState.eqPreset,
                eqBands = uiState.eqBands,
                eqBandFrequencies = uiState.eqBandFrequencies,
                eqBandLevelMin = uiState.eqBandLevelMin,
                eqBandLevelMax = uiState.eqBandLevelMax,
                eqPresetKeys = uiState.eqPresetKeys,
                eqPresetNames = uiState.eqPresetNames,
                onSetEqEnabled = { viewModel.setEqualizerEnabled(it) },
                onSetEqPreset = { viewModel.setEqualizerPreset(it) },
                onSetEqBand = { index, level -> viewModel.setEqualizerBand(index, level) },
                initialFocusRequester = soundPanelFocus,
                modifier = Modifier.fillMaxHeight().width(440.dp)
            )
        }

        AnimatedVisibility(
            visible = uiState.showQueueDrawer,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            QueueDrawer(
                queue = uiState.queue,
                currentIndex = uiState.currentIndex,
                onClose = { viewModel.toggleQueueDrawer() },
                onSongClick = { viewModel.playAt(it) },
                initialFocusRequester = queueDrawerFocus,
                modifier = Modifier.fillMaxHeight().width(400.dp)
            )
        }

        if (uiState.showExitKaraokeConfirm) {
            ExitKaraokeConfirmDialog(
                onConfirm = { viewModel.exitKaraokeMode() },
                onDismiss = { viewModel.dismissExitKaraokeConfirm() }
            )
        }
    }
}

@Composable
private fun ExitKaraokeConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cancelFocus = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "确定退出 K 歌吗？",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(24.dp))
            Row {
                ExitKaraokeDialogButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(cancelFocus)
                )
                Spacer(Modifier.width(16.dp))
                ExitKaraokeDialogButton(
                    text = "退出",
                    onClick = onConfirm
                )
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
}

@Composable
private fun ExitKaraokeDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .tvFocusable(cornerRadius = 8.dp, onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 28.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
