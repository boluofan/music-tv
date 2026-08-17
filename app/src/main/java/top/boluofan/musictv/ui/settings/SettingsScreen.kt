package top.boluofan.musictv.ui.settings

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.boluofan.musictv.domain.KeyMapping
import top.boluofan.musictv.domain.KeyMappingManager
import top.boluofan.musictv.domain.MappingTarget
import top.boluofan.musictv.ui.components.HelpDialog
import top.boluofan.musictv.ui.navigation.LocalPageScrollBridge
import top.boluofan.musictv.ui.navigation.LocalTabBarBridge
import top.boluofan.musictv.ui.theme.SelectedFocusBorder
import top.boluofan.musictv.ui.theme.seedColorFor
import top.boluofan.musictv.ui.update.UpdateViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onConfigureServer: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topFocus = remember { FocusRequester() }
    val logoutFocus = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var backButtonHasFocus by remember { mutableStateOf(false) }
    val bridge = LocalTabBarBridge.current
    val scope = rememberCoroutineScope()

    // 注册全局「返回顶部/返回底部」回调（由 MainActivity 拦截自定义按键后调用）：
    // 顶部 = 聚焦顶部返回按钮并滚回页面顶部；底部 = 聚焦最下方的退出登录按钮
    val pageScrollBridge = LocalPageScrollBridge.current
    DisposableEffect(Unit) {
        pageScrollBridge.scrollToTop = {
            scope.launch {
                runCatching { topFocus.requestFocus() }
                scrollState.animateScrollTo(0)
            }
        }
        pageScrollBridge.scrollToBottom = {
            scope.launch {
                runCatching { logoutFocus.requestFocus() }
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
        onDispose {
            pageScrollBridge.scrollToTop = null
            pageScrollBridge.scrollToBottom = null
        }
    }

    // 焦点已在返回按钮且页面在顶部时禁用，「返回键」穿透到外层 BackHandler 直接返回上一级
    BackHandler(
        enabled = bridge?.hasFocus != true && !(backButtonHasFocus && scrollState.value == 0)
    ) {
        if (scrollState.value > 0) {
            scope.launch {
                runCatching { topFocus.requestFocus() }
                scrollState.animateScrollTo(0)
            }
        } else {
            scope.launch {
                runCatching { topFocus.requestFocus() }
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { topFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack, focusRequester = topFocus, onFocusChanged = { backButtonHasFocus = it })
            Spacer(Modifier.width(16.dp))
            Text(
                text = "设置",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(24.dp))

        SettingsSection("主题模式") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeOption("跟随系统", 0, uiState.themeMode) { viewModel.setThemeMode(0) }
                ThemeOption("浅色", 1, uiState.themeMode) { viewModel.setThemeMode(1) }
                ThemeOption("深色", 2, uiState.themeMode) { viewModel.setThemeMode(2) }
                ThemeOption("暗夜", 3, uiState.themeMode) { viewModel.setThemeMode(3) }
            }
        }

        Spacer(Modifier.height(24.dp))

        SettingsSection("主题色调") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeColorOption("黛青蓝", "indigo", uiState.themeColor) { viewModel.setThemeColor("indigo") }
                ThemeColorOption("薄荷绿", "emerald", uiState.themeColor) { viewModel.setThemeColor("emerald") }
                ThemeColorOption("珊瑚粉", "sakura", uiState.themeColor) { viewModel.setThemeColor("sakura") }
                ThemeColorOption("蜜橘橙", "honey", uiState.themeColor) { viewModel.setThemeColor("honey") }
            }
        }

        Spacer(Modifier.height(24.dp))

        SettingsSection("服务器") {
            SettingsItem(
                label = "当前服务器",
                value = uiState.serverUrl.ifEmpty { "未配置" }
            )
        }

        Spacer(Modifier.height(24.dp))

        SettingsSection("音频格式（服务端转码，视频/多音轨文件不受影响）") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QualityOption("128k", "128k", uiState.audioQuality) { viewModel.setAudioQuality("128k") }
                QualityOption("320k", "320k", uiState.audioQuality) { viewModel.setAudioQuality("320k") }
                QualityOption("无损", "flac", uiState.audioQuality) { viewModel.setAudioQuality("flac") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "播放中切换将于下一首生效",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(Modifier.height(24.dp))

        SettingsSection("背景播放（退出应用后继续播放）") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionChip("是", uiState.backgroundPlayback) { viewModel.setBackgroundPlayback(true) }
                OptionChip("否", !uiState.backgroundPlayback) { viewModel.setBackgroundPlayback(false) }
            }
        }

        Spacer(Modifier.height(24.dp))

        var showKeyMappingDialog by remember { mutableStateOf(false) }
        SettingsSection("按键设置（自定义遥控器按键映射）") {
            SettingsItem(
                label = "自定义按键",
                value = "配置上 / 下 / 左 / 右 / 返回 / 确认 / 返回顶部 / 返回底部",
                onClick = { showKeyMappingDialog = true }
            )
        }
        if (showKeyMappingDialog) {
            KeyMappingDialog(
                keyMapping = uiState.keyMapping,
                onSetMapping = viewModel::setKeyMapping,
                onReset = viewModel::resetKeyMapping,
                onDismiss = { showKeyMappingDialog = false }
            )
        }

        Spacer(Modifier.height(24.dp))

        val sleepSuffix = when {
            uiState.sleepTimerRemaining > 0 -> "（剩余 ${uiState.sleepTimerRemaining} 分钟）"
            uiState.sleepAfterSongsRemaining > 0 -> "（剩余 ${uiState.sleepAfterSongsRemaining} 首）"
            else -> ""
        }
        SettingsSection("睡眠定时$sleepSuffix") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OptionChip("关闭", uiState.sleepTimerMinutes == 0 && uiState.sleepAfterSongs == 0) { viewModel.setSleepTimer(0) }
                    OptionChip("30 分钟", uiState.sleepTimerMinutes == 30) { viewModel.setSleepTimer(30) }
                    OptionChip("60 分钟", uiState.sleepTimerMinutes == 60) { viewModel.setSleepTimer(60) }
                    OptionChip("90 分钟", uiState.sleepTimerMinutes == 90) { viewModel.setSleepTimer(90) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OptionChip("播完本首", uiState.sleepAfterSongs == 1) { viewModel.setSleepAfterSongs(1) }
                    OptionChip("播完 3 首", uiState.sleepAfterSongs == 3) { viewModel.setSleepAfterSongs(3) }
                    OptionChip("播完 5 首", uiState.sleepAfterSongs == 5) { viewModel.setSleepAfterSongs(5) }
                    OptionChip("播完 10 首", uiState.sleepAfterSongs == 10) { viewModel.setSleepAfterSongs(10) }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        SettingsSection("日志") {
            SettingsItem(
                label = "导出日志",
                value = uiState.logExportStatus.ifEmpty { "导出运行日志用于排查问题" },
                onClick = { viewModel.exportLogs() }
            )
        }

        Spacer(Modifier.height(24.dp))

        var showHelpDialog by remember { mutableStateOf(false) }
        SettingsSection("帮助") {
            SettingsItem(
                label = "操作说明",
                value = "操作及按键说明",
                onClick = { showHelpDialog = true }
            )
        }
        if (showHelpDialog) {
            HelpDialog(onDismiss = { showHelpDialog = false })
        }

        Spacer(Modifier.height(24.dp))

        SettingsSection("关于") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val updateViewModel: UpdateViewModel = hiltViewModel()
            val versionName = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "未知"
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var checkUpdateFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (checkUpdateFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("版本", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.width(12.dp))
                        Text(versionName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Text(
                        text = "检查更新",
                        fontSize = 14.sp,
                        fontWeight = if (checkUpdateFocused) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (checkUpdateFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .then(
                                if (checkUpdateFocused) Modifier.border(
                                    3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
                                ) else Modifier
                            )
                            .onFocusChanged { checkUpdateFocused = it.isFocused }
                            .clickable { updateViewModel.manualCheck() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                SettingsItem(label = "项目地址", value = "github.com/boluofan/music-tv")
                SettingsItem(
                    label = "开源组件",
                    value = "Jetpack Compose · Media3 · Retrofit · Coil · Hilt"
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SettingsItem(
            label = "重启应用",
            value = "重启应用可使部分设置（如音频格式）立即生效",
            onClick = { viewModel.restartApp() }
        )

        Spacer(Modifier.height(24.dp))

        var pendingDanger by remember { mutableStateOf<DangerAction?>(null) }

        // 危险操作沉底，降低误触概率
        Column {
            Text(
                text = "危险操作",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DangerTextButton(
                    label = "清除配置",
                    onClick = { pendingDanger = DangerAction.CLEAR_CONFIG }
                )
                DangerTextButton(
                    label = "退出登录",
                    onClick = { pendingDanger = DangerAction.LOGOUT },
                    focusRequester = logoutFocus
                )
            }
        }

        when (pendingDanger) {
            DangerAction.CLEAR_CONFIG -> DangerConfirmDialog(
                title = "清除配置",
                message = "将清除服务器地址、账号信息等全部配置，并回到配置服务器页面。此操作不可撤销，确定继续吗？",
                onConfirm = {
                    pendingDanger = null
                    viewModel.clearServerConfig()
                    onConfigureServer()
                },
                onDismiss = { pendingDanger = null }
            )
            DangerAction.LOGOUT -> DangerConfirmDialog(
                title = "退出登录",
                message = "将清除当前账号的登录状态，下次使用需重新登录。此操作不可撤销，确定继续吗？",
                onConfirm = {
                    pendingDanger = null
                    onLogout()
                },
                onDismiss = { pendingDanger = null }
            )
            null -> Unit
        }
    }
}

private enum class DangerAction { CLEAR_CONFIG, LOGOUT }

@Composable
private fun DangerTextButton(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Text(
        text = label,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .then(
                if (isFocused) Modifier.border(
                    3.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable { onClick() }
            .padding(12.dp)
    )
}

@Composable
private fun DangerConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // 默认焦点落在「取消」，避免误按确认键直接执行危险操作
    val cancelFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 36.dp, vertical = 28.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                var cancelFocused by remember { mutableStateOf(false) }
                Text(
                    text = "取消",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (cancelFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (cancelFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .then(
                            if (cancelFocused) Modifier.border(
                                3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
                            ) else Modifier
                        )
                        .focusRequester(cancelFocus)
                        .onFocusChanged { cancelFocused = it.isFocused }
                        .clickable { onDismiss() }
                        .padding(horizontal = 28.dp, vertical = 10.dp)
                )
                var confirmFocused by remember { mutableStateOf(false) }
                Text(
                    text = "确认",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (confirmFocused) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (confirmFocused) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .then(
                            if (confirmFocused) Modifier.border(
                                3.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp)
                            ) else Modifier
                        )
                        .onFocusChanged { confirmFocused = it.isFocused }
                        .clickable { onConfirm() }
                        .padding(horizontal = 28.dp, vertical = 10.dp)
                )
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
}

/** 按键设置二级弹窗：列表视图（8 个功能键 + 恢复默认）与录制视图在同一个 Dialog 内切换。
 *  对话框是独立窗口，不经过 Activity 层翻译，录制时收到的即原始 keycode；
 *  录制视图捕获任意 KeyDown 完成映射，KeyUp 一并消费防止平台关闭对话框 */
@Composable
private fun KeyMappingDialog(
    keyMapping: KeyMapping,
    onSetMapping: (MappingTarget, Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var capturing by remember { mutableStateOf<MappingTarget?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    val firstRowFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    var closeFocused by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 36.dp, vertical = 28.dp)
                .onPreviewKeyEvent { event ->
                    val target = capturing ?: return@onPreviewKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            val raw = event.nativeKeyEvent.keyCode
                            val occupied = KeyMappingManager.occupiedTarget(keyMapping, target, raw)
                            when {
                                raw == KeyEvent.KEYCODE_UNKNOWN -> {
                                    hint = "无法识别该按键，请重试"
                                    true
                                }
                                raw == KeyEvent.KEYCODE_BACK && target != MappingTarget.BACK -> {
                                    capturing = null
                                    true
                                }
                                occupied != null -> {
                                    hint = "该按键已被【${occupied.displayName}键】使用"
                                    true
                                }
                                else -> {
                                    onSetMapping(target, raw)
                                    capturing = null
                                    true
                                }
                            }
                        }
                        else -> true
                    }
                }
        ) {
            val target = capturing
            if (target == null) {
                Text(
                    text = "按键设置",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "点击要修改的按键，然后在遥控器上按下您希望使用的实际按键",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = KEY_MAPPING_DIALOG_LIST_HEIGHT.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(MappingTarget.entries) { item ->
                        SettingsItem(
                            label = "${item.displayName}键",
                            value = KeyMappingManager.keyDisplayName(keyMapping.valueFor(item)),
                            onClick = {
                                capturing = item
                                hint = null
                            },
                            focusRequester = if (item == MappingTarget.UP) firstRowFocus else null
                        )
                    }
                    item {
                        SettingsItem(
                            label = "恢复默认",
                            value = "重置全部按键映射",
                            onClick = onReset
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "关闭",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (closeFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (closeFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .then(
                            if (closeFocused) Modifier.border(
                                3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
                            ) else Modifier
                        )
                        .focusRequester(closeFocus)
                        .onFocusChanged { closeFocused = it.isFocused }
                        .clickable { onDismiss() }
                        .padding(horizontal = 28.dp, vertical = 10.dp)
                )
            } else {
                Text(
                    text = "自定义按键",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (target == MappingTarget.BACK) {
                        "请按下您希望作为【「返回键」】使用的按键"
                    } else {
                        "请按下您希望作为【${target.displayName}键】使用的按键\n（按「返回键」可取消录制）"
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
                hint?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(24.dp))
                var cancelFocused by remember { mutableStateOf(false) }
                Text(
                    text = "取消",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (cancelFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (cancelFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .then(
                            if (cancelFocused) Modifier.border(
                                3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
                            ) else Modifier
                        )
                        .focusRequester(closeFocus)
                        .onFocusChanged { cancelFocused = it.isFocused }
                        .clickable { capturing = null }
                        .padding(horizontal = 28.dp, vertical = 10.dp)
                )
            }
        }
    }

    // 打开弹窗及录制取消/完成后，焦点回到第一个配置项「上键」，确认键即可直接进入录制
    LaunchedEffect(capturing) {
        if (capturing == null) runCatching { firstRowFocus.requestFocus() }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SettingsItem(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (onClick != null) Modifier
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { onClick() }
            else Modifier)
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .then(
                if (isFocused) Modifier.border(
                    3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun BackButton(onClick: () -> Unit, focusRequester: FocusRequester? = null, onFocusChanged: ((Boolean) -> Unit)? = null) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .then(
                if (isFocused) Modifier.border(
                    3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50)
                ) else Modifier
            )
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged?.invoke(it.isFocused)
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            tint = if (isFocused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ThemeOption(label: String, mode: Int, currentMode: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OptionChip(label, mode == currentMode, modifier, onClick)
}

@Composable
private fun ThemeColorOption(label: String, name: String, currentName: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val isSelected = name == currentName
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(120),
        label = "themeColorScale"
    )

    Row(
        modifier = modifier
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
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(50))
                .background(seedColorFor(name))
                .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
        )
        Text(
            text = if (isSelected) "✓ $label" else label,
            fontSize = 14.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                isFocused -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun QualityOption(label: String, value: String, currentValue: String, onClick: () -> Unit) {
    OptionChip(label, value == currentValue, Modifier, onClick)
}

@Composable
private fun OptionChip(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(120),
        label = "optionChipScale"
    )

    Text(
        text = if (isSelected) "✓ $label" else label,
        fontSize = 14.sp,
        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
        color = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isFocused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
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
                    // 选中项聚焦：白色粗描边与 ✓ 同色但更粗更亮，配合缩放一眼可辨
                    3.dp,
                    if (isSelected) SelectedFocusBorder else MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

private const val KEY_MAPPING_DIALOG_LIST_HEIGHT = 320
