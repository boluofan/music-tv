package top.boluofan.musictv.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow

// ===== 主题色调 =====
// 新增色调：在 ThemeSeeds 增加条目 + seedColorFor 追加分支，并在设置界面提供选择入口即可全局替换

object ThemeSeeds {
    const val DEFAULT_NAME = "indigo"

    /** 黛青蓝（默认，色值保持不变） */
    const val INDIGO: Long = 0xFF415F91

    /** 薄荷绿（参考 songloft-player green 主题 primary rgb(77,175,124)） */
    const val EMERALD: Long = 0xFF4DAF7C

    /** 珊瑚粉（参考 songloft-player pink 主题 primary rgb(241,130,141)） */
    const val SAKURA: Long = 0xFFF1828D

    /** 蜜橘橙（参考 songloft-player orange 主题 primary rgb(245,171,53)） */
    const val HONEY: Long = 0xFFF5AB35
}

/** 按名称取主题种子色；未知名称回退默认黛青蓝 */
fun seedColorFor(name: String): Color = when (name) {
    ThemeSeeds.DEFAULT_NAME -> Color(ThemeSeeds.INDIGO)
    "emerald" -> Color(ThemeSeeds.EMERALD)
    "sakura" -> Color(ThemeSeeds.SAKURA)
    "honey" -> Color(ThemeSeeds.HONEY)
    else -> Color(ThemeSeeds.INDIGO)
}

/** 选中项聚焦时的高对比描边（画在 primary 填充上，任何色调下都清晰） */
val SelectedFocusBorder = Color.White

// ===== 播放器深色 UI 固定色（不随主题色调变化，集中管理） =====

object PlayerColors {
    // 背景
    val Background = Color.Black
    val Scrim = Color.Black.copy(alpha = 0.6f)
    val BarBackground = Color.Black.copy(alpha = 0.8f)
    val QueueBackground = Color(0xE6111827)

    // 文字
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.8f)
    val TextTertiary = Color.White.copy(alpha = 0.7f)
    val TextMuted = Color.White.copy(alpha = 0.5f)
    val LyricsInactive = Color.White.copy(alpha = 0.75f)
    val LyricsWord = Color.White.copy(alpha = 0.85f)

    // 控件
    val ControlBg = Color.White.copy(alpha = 0.1f)
    val ControlBgFocused = Color.White.copy(alpha = 0.2f)
    val ControlBorder = Color.White.copy(alpha = 0.5f)
    val TrackBg = Color.White.copy(alpha = 0.2f)
    val TrackBgFocused = Color.White.copy(alpha = 0.35f)
    val RowCurrent = Color.White.copy(alpha = 0.15f)
    val RowFocused = Color.White.copy(alpha = 0.08f)
    val TouchEntryBg = Color.White.copy(alpha = 0.25f)
    val TouchEntryIcon = Color.White.copy(alpha = 0.85f)

    // 歌词阴影
    val LyricShadow = Shadow(
        color = Color.Black.copy(alpha = 0.8f),
        offset = Offset(0f, 2f),
        blurRadius = 8f
    )
}
