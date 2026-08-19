package top.boluofan.musictv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * 记录"点击哪个元素进入了二级界面"，返回本界面时把焦点恢复到该元素上。
 * pendingKey 随 SaveableStateHolder 一起保存，跨界面销毁重建存活。
 */
@Stable
class ScreenFocusRestorer(initialKey: String?) {
    var pendingKey by mutableStateOf(initialKey)
    val requester = FocusRequester()

    /** 在导航离开前调用，记录当前点击元素的 key */
    fun record(key: String) {
        pendingKey = key
    }
}

@Composable
fun rememberScreenFocusRestorer(): ScreenFocusRestorer = rememberSaveable(
    saver = Saver(
        save = { it.pendingKey ?: "" },
        restore = { ScreenFocusRestorer(it.ifEmpty { null }) }
    )
) { ScreenFocusRestorer(null) }

/** 匹配到待恢复 key 的元素挂上恢复用的 FocusRequester */
fun Modifier.restorableFocus(restorer: ScreenFocusRestorer, key: String): Modifier =
    if (restorer.pendingKey == key) this.focusRequester(restorer.requester) else this

/**
 * 界面进入组合后尝试恢复焦点；目标元素可能要等列表布局完成才挂载，
 * 故按帧重试若干次，成功或超次后清除 pendingKey。
 */
@Composable
fun RestoreFocusEffect(restorer: ScreenFocusRestorer) {
    LaunchedEffect(Unit) {
        if (restorer.pendingKey == null) return@LaunchedEffect
        repeat(10) {
            withFrameNanos { }
            if (runCatching { restorer.requester.requestFocus() }.isSuccess) {
                restorer.pendingKey = null
                return@LaunchedEffect
            }
        }
        restorer.pendingKey = null
    }
}

/**
 * 界面进入组合后把默认焦点落到指定元素上；
 * 有待恢复焦点（从二级界面返回）时让位给 RestoreFocusEffect。
 */
@Composable
fun DefaultFocusEffect(restorer: ScreenFocusRestorer, target: FocusRequester) {
    LaunchedEffect(Unit) {
        if (restorer.pendingKey != null) return@LaunchedEffect
        repeat(10) {
            withFrameNanos { }
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
}
