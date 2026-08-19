package top.boluofan.musictv.ui.player

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.boluofan.musictv.ui.theme.PlayerColors
import kotlinx.coroutines.launch

private const val STRENGTH_STEP = 1
private const val TAG = "SfxPanel"

/** 面板内可聚焦区，用于按键时实时计算焦点边界（不依赖可能过期的缓存状态） */
private enum class FocusZone { SFX_CHIP, STRENGTH, EQ_TOGGLE, PRESET, BAND }

/**
 * 合并音效面板：上半音效模式（单选 + 强度），下半均衡器（独立开关 + 预设 + 频段）。
 * 两组效果在 audiofx 层面独立叠加，面板只负责统一入口。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundPanel(
    sfxSupported: Boolean,
    sfxOnA2dp: Boolean,
    sfxMode: String,
    sfxStrength: Int,
    sfxModeKeys: List<String>,
    sfxModeNames: List<String>,
    sfxModeSupported: List<Boolean>,
    onSetSfxMode: (String) -> Unit,
    onSetSfxStrength: (Int) -> Unit,
    eqSupported: Boolean,
    eqEnabled: Boolean,
    eqPreset: String,
    eqBands: List<Int>,
    eqBandFrequencies: List<Int>,
    eqBandLevelMin: Int,
    eqBandLevelMax: Int,
    eqPresetKeys: List<String>,
    eqPresetNames: List<String>,
    onSetEqEnabled: (Boolean) -> Unit,
    onSetEqPreset: (String) -> Unit,
    onSetEqBand: (Int, Int) -> Unit,
    initialFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    // 当前聚焦项（区 + 序号），按键时据此实时计算边界拦截
    val focusedItem = remember { mutableStateOf<Pair<FocusZone, Int>?>(null) }
    val sfxChipBounds = remember { mutableMapOf<Int, Rect>() }
    val eqToggleBounds = remember { mutableMapOf<Int, Rect>() }
    val presetBounds = remember { mutableMapOf<Int, Rect>() }
    val bandBounds = remember { mutableMapOf<Int, Rect>() }
    var strengthBounds by remember { mutableStateOf<Rect?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var panelOriginY by remember { mutableStateOf(0f) }
    var panelViewport by remember { mutableStateOf(0) }
    val scrollMargin = with(LocalDensity.current) { 8.dp.toPx() }

    // 面板内可聚焦链：音效 chips → 强度条 → 均衡器开关 chips → 预设 chips → 频段行
    // 各段是否"贴底"决定要不要拦截方向键，避免焦点逃出面板
    val eqToggleVisible = eqSupported
    val eqContentVisible = eqSupported && eqEnabled && eqBandFrequencies.isNotEmpty()
    val strengthBottomMost = sfxSupported && !eqToggleVisible
    val eqToggleBottomMost = eqToggleVisible && !eqContentVisible

    // 按键时按最新布局与开关状态实时计算聚焦项的四向边界（布局变化后不依赖旧缓存）
    fun edgesFor(item: Pair<FocusZone, Int>?): Set<EqEdge> {
        if (item == null) return emptySet()
        val (zone, index) = item
        return when (zone) {
            FocusZone.SFX_CHIP -> rowEdgesFor(index, sfxChipBounds)
            FocusZone.STRENGTH -> if (strengthBottomMost) setOf(EqEdge.BOTTOM) else emptySet()
            FocusZone.EQ_TOGGLE -> {
                val edges = rowEdgesFor(index, eqToggleBounds).toMutableSet()
                // 上方音效区有可聚焦项时 TOP 交给音效 chips 判断
                if (sfxSupported) edges.remove(EqEdge.TOP)
                if (eqToggleBottomMost) edges.add(EqEdge.BOTTOM)
                edges
            }
            FocusZone.PRESET -> rowEdgesFor(index, presetBounds) - EqEdge.TOP
            FocusZone.BAND -> if (index == eqBands.lastIndex) setOf(EqEdge.BOTTOM) else emptySet()
        }
    }

    // 聚焦项超出面板可视区时才滚动，避免已可见时产生微小滚动动画
    fun scrollFocusedItemIntoView(item: Rect) {
        val viewport = panelViewport
        if (viewport <= 0) return
        val top = item.top - panelOriginY
        val bottom = item.bottom - panelOriginY
        val current = scrollState.value
        if (top < current + scrollMargin) {
            scope.launch { scrollState.animateScrollTo((top - scrollMargin).toInt().coerceAtLeast(0)) }
        } else if (bottom > current + viewport - scrollMargin) {
            scope.launch { scrollState.animateScrollTo((bottom - viewport + scrollMargin).toInt().coerceAtLeast(0)) }
        }
    }

    Column(
        modifier = modifier
            .background(PlayerColors.QueueBackground)
            .verticalScroll(scrollState)
            .onGloballyPositioned { coords ->
                panelOriginY = coords.boundsInRoot().top
                panelViewport = coords.size.height
            }
            .padding(16.dp)
            .onKeyEvent { event ->
                // 子项未消费的方向键若落在面板边界，在此拦截，防止焦点移出面板
                // 边界按当前聚焦项实时计算，避免开关切换后布局变化导致旧缓存失效
                val isDirection = event.key == Key.DirectionLeft ||
                    event.key == Key.DirectionRight ||
                    event.key == Key.DirectionUp ||
                    event.key == Key.DirectionDown
                val edges = if (event.type == KeyEventType.KeyDown) edgesFor(focusedItem.value) else emptySet()
                val intercept = event.type == KeyEventType.KeyDown && isEscapeKey(event.key, edges)
                if (isDirection) {
                    Log.d(TAG, "key=${event.key} type=${if (event.type == KeyEventType.KeyDown) "Down" else "Up"} " +
                        "focused=$focusedItem.value edges=$edges intercept=$intercept")
                }
                intercept
            }
    ) {
        Text(
            text = "音效",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PlayerColors.TextPrimary
        )

        Spacer(Modifier.height(12.dp))

        if (sfxOnA2dp) {
            Text(
                text = "蓝牙输出，音效可能不生效",
                fontSize = 13.sp,
                color = PlayerColors.TextMuted
            )
            Spacer(Modifier.height(8.dp))
        }

        // ===== 音效模式（audiofx 效果器，与均衡器叠加） =====
        if (sfxSupported) {
            Text(
                text = "音效模式",
                fontSize = 13.sp,
                color = PlayerColors.TextMuted
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sfxModeKeys.forEachIndexed { index, key ->
                    EqChip(
                        label = sfxModeNames.getOrNull(index) ?: key,
                        isSelected = sfxMode == key,
                        // 各模式按设备能力矩阵置灰（总开关在设置页，列表无"关闭"）
                        enabled = sfxModeSupported.getOrElse(index) { false },
                        modifier = Modifier
                            .then(
                                if (index == 0 && initialFocusRequester != null) {
                                    Modifier.focusRequester(initialFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                            .onGloballyPositioned { coords ->
                                sfxChipBounds[index] = coords.boundsInRoot()
                            },
                        onFocusChange = { focused ->
                            if (focused) {
                                Log.d(TAG, "focus SFX_CHIP[$index] true")
                                focusedItem.value = FocusZone.SFX_CHIP to index
                            } else if (focusedItem.value == FocusZone.SFX_CHIP to index) {
                                Log.d(TAG, "focus SFX_CHIP[$index] false clear")
                                focusedItem.value = null
                            }
                            if (focused) sfxChipBounds[index]?.let { scrollFocusedItemIntoView(it) }
                        },
                        onClick = { onSetSfxMode(key) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.onGloballyPositioned { coords ->
                    strengthBounds = coords.boundsInRoot()
                }
            ) {
                SoundStrengthRow(
                    strength = sfxStrength,
                    onFocusChange = { focused ->
                        if (focused) {
                            Log.d(TAG, "focus STRENGTH true")
                            focusedItem.value = FocusZone.STRENGTH to -1
                        } else if (focusedItem.value == FocusZone.STRENGTH to -1) {
                            Log.d(TAG, "focus STRENGTH false clear")
                            focusedItem.value = null
                        }
                        if (focused) strengthBounds?.let { scrollFocusedItemIntoView(it) }
                    },
                    onStep = { delta ->
                        onSetSfxStrength((sfxStrength + delta).coerceIn(0, 100))
                    }
                )
            }
            Spacer(Modifier.height(20.dp))
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "当前设备不支持音效",
                    fontSize = 14.sp,
                    color = PlayerColors.TextMuted
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // ===== 均衡器（独立开关 + 预设 + 频段） =====
        Text(
            text = "均衡器",
            fontSize = 13.sp,
            color = PlayerColors.TextMuted
        )
        Spacer(Modifier.height(8.dp))

        if (eqSupported) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("开启" to true, "关闭" to false).forEachIndexed { index, (label, value) ->
                    EqChip(
                        label = label,
                        isSelected = eqEnabled == value,
                        modifier = Modifier
                            .then(
                                // 音效区无可聚焦项时（设备不支持），初始焦点落到均衡器开关
                                if (index == 0 && !sfxSupported && initialFocusRequester != null) {
                                    Modifier.focusRequester(initialFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                            .onGloballyPositioned { coords ->
                                eqToggleBounds[index] = coords.boundsInRoot()
                            },
                        onFocusChange = { focused ->
                            if (focused) {
                                Log.d(TAG, "focus EQ_TOGGLE[$index] true")
                                focusedItem.value = FocusZone.EQ_TOGGLE to index
                            } else if (focusedItem.value == FocusZone.EQ_TOGGLE to index) {
                                Log.d(TAG, "focus EQ_TOGGLE[$index] false clear")
                                focusedItem.value = null
                            }
                            if (focused) eqToggleBounds[index]?.let { scrollFocusedItemIntoView(it) }
                        },
                        onClick = { onSetEqEnabled(value) }
                    )
                }
            }
            // 均衡器关闭时只显示开关，预设/频段不渲染
            if (eqEnabled) {
                Spacer(Modifier.height(16.dp))

                if (eqBandFrequencies.isEmpty()) {
                    // 频段数据需音频会话就绪（播放中）后才有
                    Text(
                        text = "播放歌曲后可用",
                        fontSize = 14.sp,
                        color = PlayerColors.TextMuted
                    )
                    Spacer(Modifier.height(20.dp))
                } else {
                    Text(
                        text = "预设",
                        fontSize = 13.sp,
                        color = PlayerColors.TextMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val shownPresets = eqPresetKeys.take(6)
                        shownPresets.forEachIndexed { index, key ->
                            EqChip(
                                label = eqPresetNames.getOrNull(index) ?: key,
                                isSelected = eqPreset == key,
                                modifier = Modifier
                                    .onGloballyPositioned { coords ->
                                        presetBounds[index] = coords.boundsInRoot()
                                    },
                                onFocusChange = { focused ->
                                    if (focused) {
                                        Log.d(TAG, "focus PRESET[$index] true")
                                        focusedItem.value = FocusZone.PRESET to index
                                    } else if (focusedItem.value == FocusZone.PRESET to index) {
                                        Log.d(TAG, "focus PRESET[$index] false clear")
                                        focusedItem.value = null
                                    }
                                    if (focused) presetBounds[index]?.let { scrollFocusedItemIntoView(it) }
                                },
                                onClick = { onSetEqPreset(key) }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "频段增益",
                        fontSize = 13.sp,
                        color = PlayerColors.TextMuted
                    )
                    Spacer(Modifier.height(8.dp))

                    // 面板整体可滚动，频段全量组合保证焦点可下探，聚焦时滚动到可视区
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        eqBands.forEachIndexed { index, levelDb ->
                            Box(
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    bandBounds[index] = coords.boundsInRoot()
                                }
                            ) {
                                EqBandRow(
                                    label = formatHz(eqBandFrequencies.getOrNull(index) ?: 0),
                                    levelDb = levelDb,
                                    levelMinDb = eqBandLevelMin / 100,
                                    levelMaxDb = eqBandLevelMax / 100,
                                    onFocusChange = { focused ->
                                        if (focused) {
                                            Log.d(TAG, "focus BAND[$index] true")
                                            focusedItem.value = FocusZone.BAND to index
                                        } else if (focusedItem.value == FocusZone.BAND to index) {
                                            Log.d(TAG, "focus BAND[$index] false clear")
                                            focusedItem.value = null
                                        }
                                        if (focused) bandBounds[index]?.let { scrollFocusedItemIntoView(it) }
                                    },
                                    onStep = { delta ->
                                        val next = (levelDb + delta)
                                            .coerceIn(eqBandLevelMin / 100, eqBandLevelMax / 100)
                                        onSetEqBand(index, next)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "当前设备不支持均衡器",
                    fontSize = 14.sp,
                    color = PlayerColors.TextMuted
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SoundStrengthRow(
    strength: Int,
    onFocusChange: (Boolean) -> Unit = {},
    onStep: (Int) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val progress = strength.coerceIn(0, 100) / 100f

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "强度",
                fontSize = 13.sp,
                color = if (isFocused) PlayerColors.TextPrimary else PlayerColors.TextSecondary
            )
            Text(
                text = "$strength%",
                fontSize = 13.sp,
                color = PlayerColors.TextTertiary
            )
        }
        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .onFocusChanged {
                    isFocused = it.isFocused
                    onFocusChange(it.isFocused)
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { onStep(-STRENGTH_STEP); true }
                        Key.DirectionRight -> { onStep(STRENGTH_STEP); true }
                        else -> false
                    }
                }
                .focusable(),
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
