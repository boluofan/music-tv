package top.boluofan.musictv

import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.storage.PreferencesDataStore
import top.boluofan.musictv.data.storage.ResumeSnapshotStore
import top.boluofan.musictv.domain.KeyMappingManager
import top.boluofan.musictv.domain.MappingTarget
import top.boluofan.musictv.domain.PlayMode
import top.boluofan.musictv.domain.PlayerController
import top.boluofan.musictv.ui.components.FloatingPlayerBar
import top.boluofan.musictv.ui.components.tvFocusable
import top.boluofan.musictv.ui.config.AuthSetupScreen
import top.boluofan.musictv.ui.config.AuthState
import top.boluofan.musictv.ui.config.AuthViewModel
import top.boluofan.musictv.ui.album.AlbumDetailScreen
import top.boluofan.musictv.ui.artist.ArtistDetailScreen
import top.boluofan.musictv.ui.discover.DiscoverScreen
import top.boluofan.musictv.ui.discover.LeaderboardDetailScreen
import top.boluofan.musictv.ui.home.HomeScreen
import top.boluofan.musictv.ui.home.HomeViewModel
import top.boluofan.musictv.ui.navigation.LocalPageScrollBridge
import top.boluofan.musictv.ui.navigation.LocalPlayerBarBridge
import top.boluofan.musictv.ui.navigation.LocalTabBarBridge
import top.boluofan.musictv.ui.navigation.PageScrollBridge
import top.boluofan.musictv.ui.navigation.PlayerBarBridge
import top.boluofan.musictv.ui.navigation.Screen
import top.boluofan.musictv.ui.navigation.TabBarBridge
import top.boluofan.musictv.ui.navigation.TvBottomNav
import top.boluofan.musictv.ui.navigation.stateKey
import top.boluofan.musictv.ui.playlist.PlaylistDetailScreen
import top.boluofan.musictv.ui.search.SearchScreen
import top.boluofan.musictv.ui.settings.SettingsScreen
import top.boluofan.musictv.ui.theme.TvTheme
import top.boluofan.musictv.ui.update.UpdateDialog
import top.boluofan.musictv.ui.update.UpdateViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playerController: PlayerController

    @Inject
    lateinit var keyMappingManager: KeyMappingManager

    @Inject
    lateinit var preferencesDataStore: PreferencesDataStore

    @Inject
    lateinit var resumeSnapshotStore: ResumeSnapshotStore

    /** 全局「返回顶部/返回底部」回调桥，由当前组合中的页面注册滚动实现 */
    val pageScrollBridge = PageScrollBridge()

    private var lastUserInteractionMs = SystemClock.uptimeMillis()

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastUserInteractionMs = SystemClock.uptimeMillis()
    }

    override fun onResume() {
        super.onResume()
        // 从屏保/播放器退回主页时重置，避免陈旧时间戳导致立刻再次拉起屏保
        lastUserInteractionMs = SystemClock.uptimeMillis()
    }

    /** 用户自定义按键映射：特殊功能键（返回顶部/底部）拦截处理，其余命中映射表的 keycode 翻译成标准功能键后继续分发 */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyMappingManager.matchSpecialKey(event.keyCode)) {
                MappingTarget.TOP -> {
                    pageScrollBridge.scrollToTop?.invoke()
                    return true
                }
                MappingTarget.BOTTOM -> {
                    pageScrollBridge.scrollToBottom?.invoke()
                    return true
                }
                else -> {}
            }
        }
        return super.dispatchKeyEvent(keyMappingManager.translateEvent(event))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalPageScrollBridge provides pageScrollBridge) {
                        RootScreen(
                            playerController = playerController,
                            resumeSnapshotStore = resumeSnapshotStore,
                            preferencesDataStore = preferencesDataStore,
                            onExit = { exitApp() }
                        )
                    }
                }
            }
        }

        // 屏保轮询：主页前台播放中且长时间无操作时，跳转播放器界面作为屏保
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(5_000L)
                    val s = playerController.state.value
                    if (!s.isPlaying || s.karaokeActive || s.currentSong == null) continue
                    val idleMs = SystemClock.uptimeMillis() - lastUserInteractionMs
                    if (idleMs < 60_000L) continue
                    if (!preferencesDataStore.screensaverEnabled.first()) continue
                    val timeoutMs = preferencesDataStore.screensaverTimeoutMinutes.first() * 60_000L
                    if (idleMs < timeoutMs) continue
                    lastUserInteractionMs = SystemClock.uptimeMillis()
                    runCatching {
                        startActivity(
                            Intent(this@MainActivity, PlayerActivity::class.java)
                                .putExtra(PlayerActivity.EXTRA_SCREENSAVER, true)
                        )
                    }
                }
            }
        }
    }

    private fun exitApp() {
        lifecycleScope.launch {
            val backgroundPlayback = preferencesDataStore.backgroundPlay.first()
            if (backgroundPlayback) {
                finish()
            } else {
                stopService(Intent(this@MainActivity, MusicService::class.java))
                finishAndRemoveTask()
                Process.killProcess(Process.myPid())
            }
        }
    }
}

@Composable
fun RootScreen(
    playerController: PlayerController,
    resumeSnapshotStore: ResumeSnapshotStore,
    preferencesDataStore: PreferencesDataStore,
    onExit: () -> Unit
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    when (authState) {
        is AuthState.LoggedIn -> TvApp(
            playerController = playerController,
            resumeSnapshotStore = resumeSnapshotStore,
            preferencesDataStore = preferencesDataStore,
            onExit = onExit
        )
        else -> AuthSetupScreen(authViewModel)
    }
}

@Composable
fun TvApp(
    playerController: PlayerController,
    resumeSnapshotStore: ResumeSnapshotStore,
    preferencesDataStore: PreferencesDataStore,
    onExit: () -> Unit
) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val currentScreen = backStack.last()
    val stateHolder = rememberSaveableStateHolder()
    val tabBarBridge = remember { TabBarBridge() }
    val playerBarBridge = remember { PlayerBarBridge() }
    val pageScrollBridge = remember { PageScrollBridge() }
    var showExitDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val playbackState by playerController.state.collectAsStateWithLifecycle()
    val homeViewModel: HomeViewModel = hiltViewModel()

    fun push(screen: Screen) {
        backStack.add(screen)
    }

    fun goBack() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun openPlayer() {
        runCatching { context.startActivity(Intent(context, PlayerActivity::class.java)) }
    }

    BackHandler {
        when {
            backStack.size > 1 -> goBack()
            currentScreen != Screen.Home -> backStack[0] = Screen.Home
            else -> showExitDialog = true
        }
    }

    if (showExitDialog) {
        ExitConfirmDialog(
            onConfirm = onExit,
            onDismiss = { showExitDialog = false }
        )
    }

    val authViewModel: AuthViewModel = hiltViewModel()
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { updateViewModel.autoCheckOnLaunch() }

    // 开机自动续播：登录完成即恢复上次队列与进度开始播放，悬浮播放器条随之出现；
    // 已有播放队列时跳过（后台播放存活的重启场景，或防止重复恢复）
    LaunchedEffect(Unit) {
        if (!preferencesDataStore.autoResumeOnLaunch.first()) return@LaunchedEffect
        if (playerController.state.value.queue.isNotEmpty()) return@LaunchedEffect
        resumeSnapshotStore.snapshot.first()?.let { snapshot ->
            playerController.resumePlayback(snapshot)
        }
    }

    // 自动进入播放器：开关开启且启动时存在播放中歌曲（后台播放存活，或续播即将恢复）时，
    // 等待 MediaController 连接同步出当前歌曲后直接进入全屏播放器
    LaunchedEffect(Unit) {
        if (!preferencesDataStore.autoOpenPlayerOnLaunch.first()) return@LaunchedEffect
        val hasPlayback = playerController.state.value.currentSong != null ||
            (preferencesDataStore.autoResumeOnLaunch.first() &&
                resumeSnapshotStore.snapshot.first() != null)
        if (!hasPlayback) return@LaunchedEffect
        playerController.state.first { it.currentSong != null }
        openPlayer()
    }
    UpdateDialog(
        state = updateState,
        onStartDownload = updateViewModel::startDownload,
        onIgnore = updateViewModel::ignoreVersion,
        onRetryCheck = updateViewModel::manualCheck,
        onDismiss = updateViewModel::dismiss
    )

    CompositionLocalProvider(
        LocalTabBarBridge provides tabBarBridge,
        LocalPlayerBarBridge provides playerBarBridge,
        LocalPageScrollBridge provides pageScrollBridge
    ) {
        Scaffold(
            bottomBar = {
                TvBottomNav(
                    currentScreen = currentScreen,
                    onScreenSelected = { tab ->
                        if (backStack.size != 1 || backStack[0] != tab) {
                            backStack.clear()
                            backStack.add(tab)
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                stateHolder.SaveableStateProvider(currentScreen.stateKey) {
                    when (val screen = currentScreen) {
                        Screen.Home -> HomeScreen(
                            viewModel = homeViewModel,
                            onSongClick = { queue, index -> playerController.play(queue, index); openPlayer() },
                            onPlaylistClick = { playlist ->
                                push(Screen.PlaylistDetail(playlistId = playlist.id ?: "", source = null))
                            },
                            onArtistClick = { artist, source ->
                                push(
                                    Screen.ArtistDetail(
                                        artistId = artist.id ?: "",
                                        artistName = artist.name ?: "歌手",
                                        source = source
                                    )
                                )
                            },
                            onAlbumClick = { album, source ->
                                push(
                                    Screen.AlbumDetail(
                                        albumId = album.id ?: "",
                                        albumName = album.name ?: "专辑",
                                        source = source,
                                        cover = album.img,
                                        singer = album.singer
                                    )
                                )
                            }
                        )
                        Screen.Discover -> DiscoverScreen(
                            onPlaylistClick = { playlist ->
                                push(
                                    Screen.PlaylistDetail(
                                        playlistId = playlist.id ?: "",
                                        source = playlist.source ?: "wy"
                                    )
                                )
                            },
                            onBoardClick = { board, source ->
                                push(
                                    Screen.LeaderboardDetail(
                                        bangid = board.bangid ?: board.id ?: "",
                                        boardName = board.name ?: "榜单",
                                        source = source
                                    )
                                )
                            }
                        )
                        Screen.Search -> SearchScreen(
                            onSongClick = { queue, index -> playerController.play(queue, index); openPlayer() },
                            onArtistClick = { artist, source ->
                                push(
                                    Screen.ArtistDetail(
                                        artistId = artist.id ?: "",
                                        artistName = artist.name ?: "歌手",
                                        source = source
                                    )
                                )
                            },
                            onAlbumClick = { album, source ->
                                push(
                                    Screen.AlbumDetail(
                                        albumId = album.id ?: "",
                                        albumName = album.name ?: "专辑",
                                        source = source,
                                        cover = album.coverUrl,
                                        singer = album.singerName
                                    )
                                )
                            },
                            onPlaylistClick = { playlist ->
                                // 搜索结果的歌单都来自服务器，需要传递 source
                                val source = playlist.source?.takeIf { it.isNotEmpty() }
                                push(
                                    Screen.PlaylistDetail(
                                        playlistId = playlist.id ?: "",
                                        source = source
                                    )
                                )
                            }
                        )
                        Screen.Settings -> SettingsScreen(
                            asTab = true,
                            onBack = { backStack[0] = Screen.Home },
                            onConfigureServer = { authViewModel.resetToConfig() },
                            onLogout = { authViewModel.logout() }
                        )
                        is Screen.PlaylistDetail -> PlaylistDetailScreen(
                            playlistId = screen.playlistId,
                            source = screen.source,
                            onSongClick = { queue, index -> playerController.play(queue, index); openPlayer() },
                            onShufflePlay = { queue ->
                                playerController.setPlayMode(PlayMode.RANDOM)
                                playerController.play(queue, (0 until queue.size).random())
                                openPlayer()
                            },
                            onBack = { goBack() },
                            onPlaylistDeleted = { homeViewModel.load() }
                        )
                        is Screen.LeaderboardDetail -> LeaderboardDetailScreen(
                            bangid = screen.bangid,
                            boardName = screen.boardName,
                            source = screen.source,
                            onSongClick = { queue, index -> playerController.play(queue, index); openPlayer() },
                            onShufflePlay = { queue ->
                                playerController.setPlayMode(PlayMode.RANDOM)
                                playerController.play(queue, (0 until queue.size).random())
                                openPlayer()
                            },
                            onBack = { goBack() }
                        )
                        is Screen.ArtistDetail -> ArtistDetailScreen(
                            artistId = screen.artistId,
                            artistName = screen.artistName,
                            source = screen.source,
                            onSongClick = { queue, index -> playerController.play(queue, index); openPlayer() },
                            onShufflePlay = { queue ->
                                playerController.setPlayMode(PlayMode.RANDOM)
                                playerController.play(queue, (0 until queue.size).random())
                                openPlayer()
                            },
                            onAlbumClick = { album, source ->
                                push(
                                    Screen.AlbumDetail(
                                        albumId = album.id ?: "",
                                        albumName = album.name ?: "专辑",
                                        source = source,
                                        cover = album.coverUrl,
                                        singer = album.singerName
                                    )
                                )
                            },
                            onBack = { goBack() }
                        )
                        is Screen.AlbumDetail -> AlbumDetailScreen(
                            albumId = screen.albumId,
                            albumName = screen.albumName,
                            source = screen.source,
                            cover = screen.cover,
                            singer = screen.singer,
                            onSongClick = { queue, index -> playerController.play(queue, index); openPlayer() },
                            onShufflePlay = { queue ->
                                playerController.setPlayMode(PlayMode.RANDOM)
                                playerController.play(queue, (0 until queue.size).random())
                                openPlayer()
                            },
                            onBack = { goBack() }
                        )
                        else -> PlaceholderScreen(screen.label)
                    }
                }

                // 悬浮播放器：有当前歌曲时显示，点击进入播放器页
                DisposableEffect(playbackState.currentSong != null) {
                    playerBarBridge.visible = playbackState.currentSong != null
                    onDispose { playerBarBridge.visible = false }
                }
                playbackState.currentSong?.let { song ->
                    FloatingPlayerBar(
                        title = song.name ?: "",
                        artist = song.singer,
                        coverUrl = song.picUrl,
                        isPlaying = playbackState.isPlaying,
                        onClick = {
                            context.startActivity(
                                Intent(context, PlayerActivity::class.java)
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp),
                        focusRequester = playerBarBridge.playerFocusRequester
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$label（后续阶段实现）",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ExitConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cancelFocusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "确定退出吗？",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(24.dp))
            Row {
                ExitDialogButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(cancelFocusRequester)
                )
                Spacer(Modifier.width(16.dp))
                ExitDialogButton(
                    text = "退出",
                    onClick = onConfirm
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        cancelFocusRequester.requestFocus()
    }
}

@Composable
private fun ExitDialogButton(
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
