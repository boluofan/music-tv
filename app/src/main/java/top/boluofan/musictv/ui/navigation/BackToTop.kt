package top.boluofan.musictv.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch

@Stable
class TabBarBridge {
    val tabFocusRequester = FocusRequester()
    var hasFocus by mutableStateOf(false)

    fun focusTabBar() {
        runCatching { tabFocusRequester.requestFocus() }
    }
}

val LocalTabBarBridge = compositionLocalOf<TabBarBridge?> { null }

/** 全局「返回顶部/返回底部」回调桥：当前组合中的页面注册自己的滚动实现，
 *  MainActivity 拦截到自定义按键后调用，实现任何界面快速滚动到顶/底 */
@Stable
class PageScrollBridge {
    var scrollToTop: (() -> Unit)? = null
    var scrollToBottom: (() -> Unit)? = null
}

val LocalPageScrollBridge = compositionLocalOf<PageScrollBridge> { PageScrollBridge() }

/**
 * 三段式返回键：列表非顶部时回顶并聚焦 [topFocus]；已在顶部时聚焦底部 Tab 栏；
 * 焦点已在 Tab 栏、或（跟踪了 [topFocusHasFocus] 的二级界面）焦点已在返回按钮且列表在顶部时，
 * 本 handler 禁用，返回键穿透到外层既有 BackHandler 直接返回上一级。
 * [jumpToTabBar] 为 true 时（一级 tab 页），焦点已在 [topFocus] 且列表在顶部也不穿透，
 * 而是先跳到底部 Tab 栏，再由外层处理后续返回。
 */
@Composable
fun ListBackToTopHandler(
    listState: LazyListState,
    topFocus: FocusRequester? = null,
    topFocusHasFocus: Boolean = false,
    topFocusInList: Boolean = false,
    jumpToTabBar: Boolean = false,
    enabled: Boolean = true
) {
    val bridge = LocalTabBarBridge.current
    val scope = rememberCoroutineScope()
    // 触摸模式下焦点请求无效（hasFocus 恒 false），三段式无意义且会吞掉返回键，
    // 直接禁用让返回键穿透到外层（弹退出确认/回上一级）；遥控器模式保持原有行为
    val touchMode = LocalView.current.isInTouchMode

    // 注册全局「返回顶部/返回底部」滚动回调：滚动到顶时同时把焦点移回 [topFocus]（锚点若在列表内会随滚动就位）
    val pageScrollBridge = LocalPageScrollBridge.current
    DisposableEffect(listState) {
        pageScrollBridge.scrollToTop = {
            scope.launch {
                if (topFocusInList) {
                    listState.scrollToItem(0)
                    topFocus?.let { runCatching { it.requestFocus() } }
                } else {
                    topFocus?.let { runCatching { it.requestFocus() } }
                    listState.scrollToItem(0)
                }
            }
        }
        // 「返回底部」= 焦点直接跳到底部 Tab 栏（首页/发现/搜索/我的），便于快速切换页面
        pageScrollBridge.scrollToBottom = {
            bridge?.focusTabBar()
        }
        onDispose {
            pageScrollBridge.scrollToTop = null
            pageScrollBridge.scrollToBottom = null
        }
    }
    BackHandler(
        enabled = enabled && !touchMode && bridge?.hasFocus != true &&
            !(topFocusHasFocus && !listState.canScrollBackward && !jumpToTabBar) &&
            (listState.canScrollBackward || bridge != null)
    ) {
        if (listState.canScrollBackward) {
            scope.launch {
                if (topFocusInList) {
                    listState.scrollToItem(0)
                    withFrameNanos { }
                    if (topFocus == null || runCatching { topFocus.requestFocus() }.isFailure) {
                        bridge?.focusTabBar()
                    }
                } else {
                    // 锚点在列表外恒组合：先夺焦再滚动，避免焦点项被回收后焦点乱跳
                    topFocus?.let { runCatching { it.requestFocus() } }
                    listState.scrollToItem(0)
                }
            }
        } else if (topFocus != null && !topFocusHasFocus && !topFocusInList) {
            // 列表在顶部但返回按钮尚未聚焦：先聚焦返回按钮，不跳转 Tab 栏
            scope.launch {
                topFocus?.let { runCatching { it.requestFocus() } }
            }
        } else {
            bridge?.focusTabBar()
        }
    }
}
