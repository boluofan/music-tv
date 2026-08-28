package top.boluofan.musictv.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private const val HELP_ITEM_COUNT = 5

/** 操作说明弹窗：内容可滚动，滚动到底后按「下」跳转关闭按钮；任意返回键/关闭即退出 */
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
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
                .fillMaxWidth(0.72f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 36.dp, vertical = 28.dp)
        ) {
            Text(
                text = "操作说明",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .focusable()
                    .focusRequester(listFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (listState.canScrollForward) {
                                        scope.launch {
                                            listState.animateScrollToItem((listState.firstVisibleItemIndex + 1).coerceAtMost(HELP_ITEM_COUNT - 1))
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    HelpBlock(
                        title = "返回键",
                        lines = listOf(
                            "一级界面（首页/发现/搜索/我的）：滚动中按「返回键」回顶并聚焦顶部；已在顶部再按跳到底部 Tab 栏",
                            "二级界面（歌单详情/排行榜/歌手/专辑/设置）：滚动中按「返回键」回顶并聚焦【返回】按钮；再按直接回上一级",
                            "播放器：队列抽屉/控制栏打开时按「返回键」先关闭；其余情况退出播放器（音乐后台继续播放）",
                            "连按「返回键」最终回到首页，再按弹出退出应用确认"
                        )
                    )
                }
                item {
                    HelpBlock(
                        title = "播放器快捷键",
                        lines = listOf(
                            "控制栏隐藏时：左/右键单击切上一首/下一首，长按快退/快进",
                            "控制栏隐藏时：按上/下/确认键唤出控制栏",
                            "控制栏 10 秒无操作自动隐藏",
                            "播放器内按「刷新歌词」可重新请求歌词；「队列」查看播放队列并可切换歌曲",
                            "播放模式：顺序 / 列表循环 / 单曲循环 / 随机"
                        )
                    )
                }
                item {
                    HelpBlock(
                        title = "收藏",
                        lines = listOf(
                            "歌曲行上按「收藏」按钮收藏到「我的收藏」（首页歌单区展示）",
                            "歌曲行按「加歌单」按钮把歌曲加入自定义歌单",
                            "歌手页/专辑页右上角可收藏歌手/专辑，收藏后在首页「收藏歌手/收藏专辑」区展示",
                            "首页收藏区选中整行按「确认」进入详情，按行内心形按钮取消收藏"
                        )
                    )
                }
                item {
                    HelpBlock(
                        title = "搜索",
                        lines = listOf(
                            "搜索页使用自定义 TV 键盘输入，可搜索歌曲/歌手/专辑/歌单",
                            "搜索历史一键清空；结果按歌曲/歌手/专辑/歌单分页切换"
                        )
                    )
                }
                item {
                    HelpBlock(
                        title = "自定义按键",
                        lines = listOf(
                            "在「按键设置」中按下遥控器实际按键，即可映射到上/下/左/右/返回/确认/返回顶部/返回底部",
                            "映射后应用内所有界面（含播放器）按自定义键生效，原默认键仍可用",
                            "适合 keycode 非标的遥控器与车机方向盘按键（如方向盘按键映射为左/右键实现切歌）",
                            "录制时按「返回键」可取消；「恢复默认」可清除全部映射",
                            "自定义「返回顶部」键：任意长列表一键回顶并聚焦顶部按钮",
                            "自定义「返回底部」键：焦点直达底部 Tab 栏（设置页为退出登录按钮）"
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            var closeFocused by remember { mutableStateOf(false) }
            Text(
                text = "我知道了",
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
        }
    }

    // 默认焦点落在说明列表，滚动到底后按「下」跳转关闭按钮
    LaunchedEffect(Unit) { runCatching { listFocus.requestFocus() } }
}

@Composable
private fun HelpBlock(title: String, lines: List<String>) {
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
