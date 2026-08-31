package top.boluofan.musictv.ui.update

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import top.boluofan.musictv.BuildConfig
import top.boluofan.musictv.data.model.UpdateInfo
import top.boluofan.musictv.util.ApkInstaller
import java.util.Locale

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onStartDownload: () -> Unit,
    onIgnore: () -> Unit,
    onRetryCheck: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state == UpdateUiState.Idle) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                UpdateUiState.Idle -> {}
                UpdateUiState.Checking -> CheckingPanel()
                UpdateUiState.UpToDate -> UpToDatePanel(onDismiss)
                is UpdateUiState.CheckFailed -> CheckFailedPanel(state.message, onRetryCheck, onDismiss)
                is UpdateUiState.UpdateAvailable -> AvailablePanel(state, onStartDownload, onIgnore, onDismiss)
                is UpdateUiState.Downloading -> DownloadingPanel(state, onDismiss)
                is UpdateUiState.DownloadFailed -> DownloadFailedPanel(state.message, onStartDownload, onDismiss)
                is UpdateUiState.ReadyToInstall -> ReadyToInstallPanel(state, onDismiss)
            }
        }
    }
}

/** 更新版本的展示名：dev 预览版不加 "v" 前缀，稳定版为 "v版本名" */
private fun UpdateInfo.displayName(): String = if (isDev) "dev 预览版" else "v$versionName"

@Composable
private fun CheckingPanel() {
    DialogTitle("检查更新")
    Spacer(Modifier.height(16.dp))
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    DialogBody("正在检查更新…")
}

@Composable
private fun UpToDatePanel(onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    DialogTitle("已是最新版本")
    Spacer(Modifier.height(12.dp))
    DialogBody("当前版本 v${BuildConfig.VERSION_NAME}")
    Spacer(Modifier.height(24.dp))
    DialogButton("确定", onClick = onDismiss, modifier = Modifier.focusRequester(focus))
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun CheckFailedPanel(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    DialogTitle("检查更新失败")
    Spacer(Modifier.height(12.dp))
    DialogBody(message)
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DialogButton("重试", onClick = onRetry, modifier = Modifier.focusRequester(focus))
        DialogButton("关闭", onClick = onDismiss)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun AvailablePanel(
    state: UpdateUiState.UpdateAvailable,
    onStartDownload: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit
) {
    val listFocus = remember { FocusRequester() }
    val buttonFocus = remember { FocusRequester() }
    val info = state.info
    DialogTitle(if (info.isDev) "发现新 dev 预览版" else "发现新版本 ${info.displayName()}")
    Spacer(Modifier.height(12.dp))
    DialogBody(
        if (info.isDev) "当前版本 v${BuildConfig.VERSION_NAME} → ${info.displayName()}" +
            (info.buildTime?.let { "（构建于 $it）" } ?: "")
        else "当前版本 v${BuildConfig.VERSION_NAME} → 新版本 ${info.displayName()}"
    )

    val notes = remember(state.info.releaseNotes) { parseNotes(state.info.releaseNotes.orEmpty()) }
    if (notes.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "更新内容",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .focusable()
                .focusRequester(listFocus)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (listState.canScrollForward) {
                                    scope.launch {
                                        listState.animateScrollToItem((listState.firstVisibleItemIndex + 1).coerceAtMost(notes.lastIndex))
                                    }
                                    true
                                } else {
                                    buttonFocus.requestFocus()
                                    true
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (listState.canScrollBackward) {
                                    scope.launch {
                                        listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
                                    }
                                    true
                                } else false
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            items(notes) { line ->
                when (line) {
                    is NotesLine.Heading -> Text(
                        text = line.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                    is NotesLine.Item -> Text(
                        text = "• ${line.text}",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DialogButton("立即更新", onClick = onStartDownload, modifier = Modifier.focusRequester(buttonFocus))
        DialogButton("稍后再说", onClick = onDismiss)
        if (state.fromAutoCheck) {
            DialogButton("忽略此版本", onClick = onIgnore)
        }
    }
    LaunchedEffect(Unit) {
        runCatching { (if (notes.isEmpty()) buttonFocus else listFocus).requestFocus() }
    }
}

@Composable
private fun DownloadingPanel(state: UpdateUiState.Downloading, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    DialogTitle("正在下载 ${state.info.displayName()}")
    Spacer(Modifier.height(16.dp))
    if (state.totalBytes > 0) {
        val fraction = (state.bytesRead.toFloat() / state.totalBytes).coerceIn(0f, 1f)
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        DialogBody(
            "${(fraction * 100).toInt()}% · ${formatMb(state.bytesRead)}/${formatMb(state.totalBytes)}" +
                if (state.mirrorLabel.isNotEmpty()) " · ${state.mirrorLabel}" else ""
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        DialogBody(
            if (state.mirrorLabel.isEmpty()) "正在连接…"
            else "已下载 ${formatMb(state.bytesRead)} · ${state.mirrorLabel}"
        )
    }
    Spacer(Modifier.height(24.dp))
    DialogButton("取消", onClick = onCancel, modifier = Modifier.focusRequester(focus))
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun DownloadFailedPanel(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    DialogTitle("下载失败")
    Spacer(Modifier.height(12.dp))
    DialogBody(message)
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DialogButton("重试", onClick = onRetry, modifier = Modifier.focusRequester(focus))
        DialogButton("取消", onClick = onDismiss)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun ReadyToInstallPanel(state: UpdateUiState.ReadyToInstall, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var installFailed by remember { mutableStateOf(false) }

    DialogTitle("下载完成")
    Spacer(Modifier.height(12.dp))
    DialogBody(
        if (installFailed) "无法调起系统安装器，请在设置中允许安装未知应用后重试，或到项目主页手动下载"
        else "即将调起系统安装器安装 ${state.info.displayName()}"
    )
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DialogButton(
            "重新安装",
            onClick = { installFailed = !ApkInstaller.install(context, state.apkFile) },
            modifier = Modifier.focusRequester(focus)
        )
        DialogButton("关闭", onClick = onDismiss)
    }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        installFailed = !ApkInstaller.install(context, state.apkFile)
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun DialogBody(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    )
}

@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
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
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 10.dp)
    )
}

private fun formatMb(bytes: Long): String =
    String.format(Locale.US, "%.1fMB", bytes / 1024f / 1024f)

private sealed interface NotesLine {
    val text: String

    data class Heading(override val text: String) : NotesLine
    data class Item(override val text: String) : NotesLine
}

/** 解析 release_notes 的轻量 markdown：`#` 标题行与 `-` 列表项，其余按列表项处理 */
private fun parseNotes(notes: String): List<NotesLine> =
    notes.lines().mapNotNull { raw ->
        val line = raw.trim()
        when {
            line.startsWith("###") -> NotesLine.Heading(stripInlineMarkdown(line.removePrefix("###").trim()))
            line.startsWith("##") -> NotesLine.Heading(stripInlineMarkdown(line.removePrefix("##").trim()))
            line.startsWith("#") -> NotesLine.Heading(stripInlineMarkdown(line.removePrefix("#").trim()))
            line.startsWith("-") -> NotesLine.Item(stripInlineMarkdown(line.removePrefix("-").trim()))
            line.isBlank() -> null
            else -> NotesLine.Item(stripInlineMarkdown(line))
        }
    }

/** 去掉行内 `*强调*`、`` `代码` ``、`[文字](链接)` 的 markdown 符号，只留文字；再转换 gitmoji 短代码 */
private fun stripInlineMarkdown(text: String): String =
    replaceEmojiShortcodes(
        text.replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("`(.+?)`"), "$1")
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
    )

// gitmoji 短代码 → emoji（CI changelog 的 useGitmojis 生成，覆盖常用类型；未收录的保留原样）
private val EMOJI_MAP = mapOf(
    ":sparkles:" to "✨",
    ":bug:" to "🐛",
    ":memo:" to "📝",
    ":lipstick:" to "💄",
    ":recycle:" to "♻️",
    ":zap:" to "⚡️",
    ":white_check_mark:" to "✅",
    ":construction_worker:" to "👷",
    ":green_heart:" to "💚",
    ":wrench:" to "🔧",
    ":rewind:" to "⏪️",
    ":rocket:" to "🚀",
    ":tada:" to "🎉",
    ":bookmark:" to "🔖",
    ":ambulance:" to "🚑️",
    ":fire:" to "🔥",
    ":art:" to "🎨",
    ":lock:" to "🔒",
    ":rotating_light:" to "🚨",
    ":construction:" to "🚧",
    ":arrow_down:" to "⬇️",
    ":arrow_up:" to "⬆️",
    ":pushpin:" to "📌",
    ":chart_with_upwards_trend:" to "📈",
    ":heavy_plus_sign:" to "➕",
    ":heavy_minus_sign:" to "➖",
    ":globe_with_meridians:" to "🌐",
    ":pencil2:" to "✏️",
    ":hankey:" to "💩",
    ":twisted_rightwards_arrows:" to "🔀",
    ":package:" to "📦",
    ":alien:" to "👽️",
    ":truck:" to "🚚",
    ":boom:" to "💥",
    ":bulb:" to "💡",
    ":loud_sound:" to "🔊",
    ":mute:" to "🔇",
    ":bento:" to "🍱",
    ":wheelchair:" to "♿️",
    ":children_crossing:" to "🚸",
    ":iphone:" to "📱",
    ":see_no_evil:" to "🙈",
    ":necktie:" to "👔",
    ":stethoscope:" to "🩺",
    ":bricks:" to "🧱",
    ":technologist:" to "🧑‍💻",
    ":money_with_wings:" to "💸",
    ":thread:" to "🧵",
    ":safety_vest:" to "🦺",
    ":goal_net:" to "🥅",
    ":sos:" to "🆘",
    ":adhesive_bandage:" to "🩹",
    ":building_construction:" to "🏗️",
    ":passport_control:" to "🛂",
    ":label:" to "🏷️",
    ":triangular_flag_on_post:" to "🚩",
    ":dizzy:" to "💫",
    ":wastebasket:" to "🗑️",
    ":coffin:" to "⚰️",
    ":test_tube:" to "🧪",
    ":gem:" to "💎",
    ":book:" to "📖",
    ":broom:" to "🧹",
    ":seedling:" to "🌱",
    ":gift:" to "🎁",
    ":alembic:" to "⚗️",
    ":egg:" to "🥚",
    ":camera_flash:" to "📸",
    ":page_facing_up:" to "📄",
    ":speech_balloon:" to "💬",
    ":card_file_box:" to "🗃️"
)

private val EMOJI_REGEX = Regex(":([a-z0-9_+-]+):")

private fun replaceEmojiShortcodes(text: String): String =
    EMOJI_REGEX.replace(text) { match -> EMOJI_MAP[match.value] ?: match.value }
