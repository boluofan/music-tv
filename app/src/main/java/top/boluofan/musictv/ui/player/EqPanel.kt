package top.boluofan.musictv.ui.player

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.boluofan.musictv.ui.theme.PlayerColors
import top.boluofan.musictv.ui.theme.SelectedFocusBorder
import kotlin.math.abs

@Composable
internal fun EqChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFocusChange: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(120),
        label = "eqChipScale"
    )

    val shape = RoundedCornerShape(16.dp)
    Text(
        text = label,
        fontSize = 14.sp,
        // 选中态用加粗；聚焦不加粗（字重变化会改文本宽度，FlowRow 重排引起焦点抖动）
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color = when {
            !enabled -> MaterialTheme.colorScheme.onSurface
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isFocused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = if (enabled) {
            modifier
                .scale(scale)
                .clip(shape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .then(
                    if (isFocused) Modifier.border(
                        3.dp,
                        if (isSelected) SelectedFocusBorder else MaterialTheme.colorScheme.primary,
                        shape
                    ) else Modifier
                )
                .onFocusChanged {
                    isFocused = it.isFocused
                    onFocusChange(it.isFocused)
                }
                .clickable { onClick() }
        } else {
            // 设备不支持的选项：不可聚焦、置灰
            modifier
                .alpha(0.35f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        }
            .padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
internal fun EqBandRow(
    label: String,
    levelDb: Int,
    levelMinDb: Int,
    levelMaxDb: Int,
    enabled: Boolean = true,
    onFocusChange: (Boolean) -> Unit = {},
    onStep: (Int) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val progress = if (levelMaxDb > levelMinDb) {
        (levelDb - levelMinDb).toFloat() / (levelMaxDb - levelMinDb).toFloat()
    } else 0f

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (isFocused) PlayerColors.TextPrimary else PlayerColors.TextSecondary
            )
            Text(
                text = "${if (levelDb > 0) "+" else ""}$levelDb dB",
                fontSize = 13.sp,
                color = PlayerColors.TextTertiary
            )
        }
        Spacer(Modifier.height(4.dp))

        Box(
            modifier = if (enabled) {
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        onFocusChange(it.isFocused)
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> { onStep(-BAND_STEP_DB); true }
                            Key.DirectionRight -> { onStep(BAND_STEP_DB); true }
                            else -> false
                        }
                    }
                    .focusable()
            } else {
                // 均衡器关闭时：不可聚焦、置灰
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .alpha(0.35f)
            },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isFocused) 6.dp else 4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isFocused) PlayerColors.TrackBgFocused else PlayerColors.TrackBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f)),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(PlayerColors.TextPrimary)
                    )
                }
            }
        }
    }
}

internal fun formatHz(freq: Int): String = when {
    freq >= 1000 && freq % 1000 == 0 -> "${freq / 1000} kHz"
    freq >= 1000 -> "%.1f kHz".format(freq / 1000f)
    else -> "$freq Hz"
}

/** 面板焦点边界：记录当前焦点项所在的边界，用于拦截会把焦点带出面板的方向键 */
internal enum class EqEdge { LEFT, RIGHT, TOP, BOTTOM }

/** 按实测位置判断 chip 是否处于所在行的最左/最右或整个 chip 区的最顶行 */
internal fun rowEdgesFor(index: Int, bounds: Map<Int, Rect>): Set<EqEdge> {
    val self = bounds[index] ?: return emptySet()
    val sameRow = bounds.filterValues { abs(it.top - self.top) <= 2f }
    return buildSet {
        if (sameRow.all { it.value.left >= self.left }) add(EqEdge.LEFT)
        if (sameRow.all { it.value.right <= self.right }) add(EqEdge.RIGHT)
        if (bounds.all { it.value.top >= self.top }) add(EqEdge.TOP)
    }
}

internal fun isEscapeKey(key: Key, edges: Set<EqEdge>): Boolean = when (key) {
    Key.DirectionLeft -> EqEdge.LEFT in edges
    Key.DirectionRight -> EqEdge.RIGHT in edges
    Key.DirectionUp -> EqEdge.TOP in edges
    Key.DirectionDown -> EqEdge.BOTTOM in edges
    else -> false
}

private const val BAND_STEP_DB = 1
