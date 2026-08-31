package top.boluofan.musictv.ui.components

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private const val DISCLAIMER_ITEM_COUNT = 4

/**
 * 首次启动的版权说明/免责声明弹窗。
 * 「操作说明」按钮跳转 HelpDialog；任意方式关闭（含返回键）即视为已展示，
 * 由调用方负责标记 DataStore，之后不再弹出。
 */
@Composable
fun DisclaimerDialog(
    onOpenHelp: () -> Unit,
    onDismiss: () -> Unit
) {
    val listFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "版权与免责声明",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .focusable()
                    .focusRequester(listFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (listState.canScrollForward) {
                                        scope.launch {
                                            listState.animateScrollToItem((listState.firstVisibleItemIndex + 1).coerceAtMost(DISCLAIMER_ITEM_COUNT - 1))
                                        }
                                        true
                                    } else {
                                        closeFocus.requestFocus()
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
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    DisclaimerBlock(
                        title = "版权说明",
                        lines = listOf(
                            "Music TV 是 TV 端音乐播放器，前端基于开源项目 songloft-tv 开发，基于 Apache-2.0 协议开源发布",
                            "本应用不包含、不存储任何音乐资源，所有音乐版权归原权利人所有"
                        )
                    )
                }
                item {
                    DisclaimerBlock(
                        title = "免责声明",
                        lines = listOf(
                            "本应用仅作为播放客户端连接您自建的音乐服务（LX Server），不提供、不生产任何音乐内容",
                            "请确保您使用的服务器及其中内容均已获得合法授权，并仅将本应用用于个人合法用途",
                            "因使用本应用、连接第三方服务器或播放未授权内容而产生的任何纠纷与后果，由使用者自行承担，与开发者无关"
                        )
                    )
                }
                item {
                    DisclaimerBlock(
                        title = "隐私说明",
                        lines = listOf(
                            "应用仅在本机保存您填写的服务器地址与登录凭证，用于连接您的服务器，不会收集或上传任何个人信息"
                        )
                    )
                }
                item {
                    DisclaimerBlock(
                        title = "使用安全声明",
                        lines = listOf(
                            "本应用面向 Android TV 及横屏大屏设备设计，未对车载环境做任何适配与安全优化",
                            "严禁在驾驶过程中操作本应用（包括触控、遥控器或任何交互），由此导致的一切事故与后果由使用者自行承担"
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DisclaimerButton(
                    text = "操作说明",
                    onClick = onOpenHelp
                )
                DisclaimerButton(
                    text = "我知道了",
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(closeFocus)
                )
            }
        }
    }

    // 默认焦点落在说明列表，滚动到底后按「下」跳转「我知道了」按钮
    LaunchedEffect(Unit) { runCatching { listFocus.requestFocus() } }
}

@Composable
private fun DisclaimerBlock(title: String, lines: List<String>) {
    Column {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        lines.forEach { line ->
            Text(
                text = "· $line",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun DisclaimerButton(
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
