package top.boluofan.musictv.domain

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.boluofan.musictv.data.storage.PreferencesDataStore
import javax.inject.Inject
import javax.inject.Singleton

/** 应用所需的 9 个功能键，standardKeyCode 为分发到 Compose 层的标准 keycode；
 *  TOP/BOTTOM/ACCOMPANIMENT 是特殊业务键（快速滚动到顶部/底部、K 歌原伴唱切换），无标准键，在 Activity 层拦截处理 */
enum class MappingTarget(val standardKeyCode: Int, val displayName: String) {
    UP(KeyEvent.KEYCODE_DPAD_UP, "上"),
    DOWN(KeyEvent.KEYCODE_DPAD_DOWN, "下"),
    LEFT(KeyEvent.KEYCODE_DPAD_LEFT, "左"),
    RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT, "右"),
    BACK(KeyEvent.KEYCODE_BACK, "返回"),
    CONFIRM(KeyEvent.KEYCODE_DPAD_CENTER, "确认"),
    TOP(-1, "返回顶部"),
    BOTTOM(-1, "返回底部"),
    ACCOMPANIMENT(-1, "原伴唱切换")
}

/** 每个字段是用户物理按键的原始 keycode，0 = 未自定义（跟随系统默认键） */
data class KeyMapping(
    val up: Int = 0,
    val down: Int = 0,
    val left: Int = 0,
    val right: Int = 0,
    val back: Int = 0,
    val confirm: Int = 0,
    val top: Int = 0,
    val bottom: Int = 0,
    val accompaniment: Int = 0
) {
    fun valueFor(target: MappingTarget): Int = when (target) {
        MappingTarget.UP -> up
        MappingTarget.DOWN -> down
        MappingTarget.LEFT -> left
        MappingTarget.RIGHT -> right
        MappingTarget.BACK -> back
        MappingTarget.CONFIRM -> confirm
        MappingTarget.TOP -> top
        MappingTarget.BOTTOM -> bottom
        MappingTarget.ACCOMPANIMENT -> accompaniment
    }
}

/** 全局按键映射：把用户自定义的物理按键 keycode 翻译成标准功能键 keycode，
 *  MainActivity / PlayerActivity 在 dispatchKeyEvent 里接入 translateEvent */
@Singleton
class KeyMappingManager @Inject constructor(
    private val dataStore: PreferencesDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _keyMapping = MutableStateFlow(KeyMapping())
    val keyMapping: StateFlow<KeyMapping> = _keyMapping.asStateFlow()

    init {
        scope.launch {
            dataStore.keyMapping.collect { _keyMapping.value = it }
        }
    }

    /** 命中映射表返回对应功能键的标准 keycode，未命中返回原值；0（KEYCODE_UNKNOWN）不参与映射 */
    fun translateKeyCode(raw: Int): Int {
        if (raw == 0) return raw
        val m = _keyMapping.value
        return when (raw) {
            m.up -> MappingTarget.UP.standardKeyCode
            m.down -> MappingTarget.DOWN.standardKeyCode
            m.left -> MappingTarget.LEFT.standardKeyCode
            m.right -> MappingTarget.RIGHT.standardKeyCode
            m.back -> MappingTarget.BACK.standardKeyCode
            m.confirm -> MappingTarget.CONFIRM.standardKeyCode
            else -> raw
        }
    }

    /** 命中「返回顶部/返回底部/原伴唱切换」映射返回对应功能键，未命中返回 null；0 不参与映射 */
    fun matchSpecialKey(raw: Int): MappingTarget? {
        if (raw == 0) return null
        val m = _keyMapping.value
        return when (raw) {
            m.top -> MappingTarget.TOP
            m.bottom -> MappingTarget.BOTTOM
            m.accompaniment -> MappingTarget.ACCOMPANIMENT
            else -> null
        }
    }

    /** 翻译 KeyEvent（DOWN/UP 一致翻译，保留长按/扫描码等全部属性）；无需翻译时返回原事件 */
    fun translateEvent(event: KeyEvent): KeyEvent {
        val mapped = translateKeyCode(event.keyCode)
        if (mapped == event.keyCode) return event
        return KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            mapped,
            event.repeatCount,
            event.metaState,
            event.deviceId,
            event.scanCode,
            event.flags,
            event.source
        )
    }

    companion object {
        /** 返回占用了 keyCode 的其它功能键（排除 exclude 自身），无占用返回 null */
        fun occupiedTarget(keyMapping: KeyMapping, exclude: MappingTarget, keyCode: Int): MappingTarget? =
            MappingTarget.entries.firstOrNull {
                it != exclude && keyMapping.valueFor(it) == keyCode
            }

        /** keycode → 用户可读名称，0 = 未设置 */
        fun keyDisplayName(keyCode: Int): String = when (keyCode) {
            0 -> "未设置"
            KeyEvent.KEYCODE_BACK -> "返回键"
            KeyEvent.KEYCODE_ESCAPE -> "ESC"
            KeyEvent.KEYCODE_DPAD_UP -> "上键"
            KeyEvent.KEYCODE_DPAD_DOWN -> "下键"
            KeyEvent.KEYCODE_DPAD_LEFT -> "左键"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "右键"
            KeyEvent.KEYCODE_DPAD_CENTER -> "确认键"
            KeyEvent.KEYCODE_ENTER -> "回车键"
            KeyEvent.KEYCODE_NUMPAD_ENTER -> "小键盘回车"
            KeyEvent.KEYCODE_MENU -> "菜单键"
            KeyEvent.KEYCODE_HOME -> "主页键"
            KeyEvent.KEYCODE_PAGE_UP -> "上翻页"
            KeyEvent.KEYCODE_PAGE_DOWN -> "下翻页"
            KeyEvent.KEYCODE_MOVE_HOME -> "移动到顶部"
            KeyEvent.KEYCODE_MOVE_END -> "移动到底部"
            KeyEvent.KEYCODE_VOLUME_UP -> "音量加"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "音量减"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "播放/暂停"
            KeyEvent.KEYCODE_MEDIA_PLAY -> "播放"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "暂停"
            KeyEvent.KEYCODE_MEDIA_NEXT -> "下一首"
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "上一首"
            KeyEvent.KEYCODE_TV_INPUT -> "输入源"
            KeyEvent.KEYCODE_CHANNEL_UP -> "频道加"
            KeyEvent.KEYCODE_CHANNEL_DOWN -> "频道减"
            KeyEvent.KEYCODE_NUMPAD_0, KeyEvent.KEYCODE_0 -> "0"
            KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.KEYCODE_1 -> "1"
            KeyEvent.KEYCODE_NUMPAD_2, KeyEvent.KEYCODE_2 -> "2"
            KeyEvent.KEYCODE_NUMPAD_3, KeyEvent.KEYCODE_3 -> "3"
            KeyEvent.KEYCODE_NUMPAD_4, KeyEvent.KEYCODE_4 -> "4"
            KeyEvent.KEYCODE_NUMPAD_5, KeyEvent.KEYCODE_5 -> "5"
            KeyEvent.KEYCODE_NUMPAD_6, KeyEvent.KEYCODE_6 -> "6"
            KeyEvent.KEYCODE_NUMPAD_7, KeyEvent.KEYCODE_7 -> "7"
            KeyEvent.KEYCODE_NUMPAD_8, KeyEvent.KEYCODE_8 -> "8"
            KeyEvent.KEYCODE_NUMPAD_9, KeyEvent.KEYCODE_9 -> "9"
            else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
        }
    }
}
