package top.boluofan.musictv.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.boluofan.musictv.data.model.LyricLine
import top.boluofan.musictv.data.model.LyricWord
import top.boluofan.musictv.ui.theme.PlayerColors

@Composable
fun LyricsPanel(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    currentPosition: Long,
    modifier: Modifier = Modifier,
    highlightColor: Color = PlayerColors.TextPrimary,
    fontSize: Int = 30
) {
    val listState = rememberLazyListState()

    val activeSize = fontSize.sp
    val activeLineHeight = (fontSize * 42 / 30).sp
    val inactiveSize = (fontSize * 22 / 30).sp
    val inactiveLineHeight = (fontSize * 30 / 30).sp
    val translationSize = (fontSize * 16 / 30).sp

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < lyrics.size) {
            listState.animateScrollToItem(currentIndex.coerceAtLeast(0))
        }
    }

    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无歌词",
                fontSize = 18.sp,
                color = PlayerColors.LyricsInactive
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            contentPadding = PaddingValues(vertical = 200.dp)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = index == currentIndex
                val distance = kotlin.math.abs(index - currentIndex)
                val alpha = if (isActive) 1f else (0.85f - 0.08f * distance).coerceIn(0.45f, 0.85f)

                if (isActive && line.hasWords) {
                    // 逐字高亮播放行（参考 NASMusicTV 双层裁剪方案）
                    SmoothWordHighlightLine(
                        line = line,
                        progressMs = currentPosition,
                        highlightColor = highlightColor,
                        fontSize = fontSize
                    )
                } else {
                    // 普通行或预览行（带阴影）
                    Text(
                        text = line.text.ifEmpty { "···" },
                        fontSize = if (isActive) activeSize else inactiveSize,
                        lineHeight = if (isActive) activeLineHeight else inactiveLineHeight,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) highlightColor else PlayerColors.TextPrimary.copy(alpha = alpha),
                        style = TextStyle(shadow = PlayerColors.LyricShadow),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    )
                }

                if (isActive && line.translation != null) {
                    Text(
                        text = line.translation,
                        fontSize = translationSize,
                        color = PlayerColors.LyricsWord,
                        style = TextStyle(shadow = PlayerColors.LyricShadow),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 平滑逐字高亮显示（参考 NASMusicTV 的双层裁剪方案）
 * 
 * 核心原理：
 * 1. 底层：灰色整行文本（未播放状态）
 * 2. 顶层：同文本按进度裁剪揭示的高亮副本
 * 3. 使用 TextLayoutResult 获取每个字符的像素位置
 * 4. 边界精确到浮点坐标，可落在半个字中间实现连续扫动
 */
@Composable
private fun SmoothWordHighlightLine(
    line: LyricLine,
    progressMs: Long,
    highlightColor: Color,
    fontSize: Int = 30
) {
    val words = line.words ?: return
    
    var layout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
    
    // 使用 Box 实现双层叠加，两个 Text 必须完全相同以确保坐标对齐
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // 底层：灰色预览
        Text(
            text = line.text,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 42 / 30).sp,
            fontWeight = FontWeight.Bold,
            color = PlayerColors.LyricsInactive,
            textAlign = TextAlign.Center,
            onTextLayout = { layout = it },
            modifier = Modifier.wrapContentWidth()
        )

        // 顶层：高亮裁剪（使用与底层完全相同的配置）
        val lr = layout
        if (lr != null && words.isNotEmpty()) {
            Text(
                text = line.text,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 42 / 30).sp,
                fontWeight = FontWeight.Bold,
                color = highlightColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .wrapContentWidth()
                    .drawWithContent {
                        var remainingChars = calculateCoveredChars(progressMs, words)
                        
                        // 逐行裁剪多行文本
                        for (lineIdx in 0 until lr.lineCount) {
                            val lineStart = lr.getLineStart(lineIdx)
                            val lineEnd = lr.getLineEnd(lineIdx)
                            val lineLen = lineEnd - lineStart
                            if (lineLen <= 0) continue
                            if (remainingChars <= 0f) break

                            val take = minOf(remainingChars, lineLen.toFloat())
                            val boundaryX = if (take >= lineLen) {
                                // 本行已整体覆盖，边界取最右侧
                                lr.getLineRight(lineIdx)
                            } else {
                                // 部分覆盖：在两个字符间插值计算边界 X
                                val base = take.toInt()
                                val frac = take - base
                                val boundaryOffset = lineStart + base
                                val x1 = lr.getHorizontalPosition(boundaryOffset, usePrimaryDirection = true)
                                val x2 = if (boundaryOffset + 1 >= lineEnd) {
                                    lr.getLineRight(lineIdx)
                                } else {
                                    lr.getHorizontalPosition(boundaryOffset + 1, usePrimaryDirection = true)
                                }
                                x1 + (x2 - x1) * frac
                            }

                            clipRect(
                                left = 0f,
                                top = lr.getLineTop(lineIdx),
                                right = boundaryX,
                                bottom = lr.getLineBottom(lineIdx)
                            ) {
                                this@drawWithContent.drawContent()
                            }
                            remainingChars -= take
                        }
                    }
            )
        }
    }
}

/**
 * 计算当前应高亮的字符数（含小数 → 半个字）
 * 
 * 利用字级时间戳统计：
 * - 已完成唱的字：end <= progressMs → 全部点亮
 * - 正在唱的字：start < progressMs < end → 按进度百分比点亮
 * - 未开始的字：progressMs <= start → 不点亮
 */
private fun calculateCoveredChars(
    progressMs: Long,
    words: List<LyricWord>
): Float {
    var totalChars = 0f
    var lastCompletedWordIdx = -1
    
    // 找到最后一个完整唱完的字（end <= progressMs）
    for (i in words.indices) {
        if (progressMs >= words[i].end) {
            lastCompletedWordIdx = i
        } else {
            break
        }
    }
    
    // 累加所有已完成字的字符数
    for (i in 0..lastCompletedWordIdx) {
        totalChars += words[i].text.length
    }
    
    // 处理正在唱的字（索引是 lastCompletedWordIdx + 1）
    if (lastCompletedWordIdx >= 0 && lastCompletedWordIdx < words.size - 1) {
        val currentWord = words[lastCompletedWordIdx + 1]
        if (currentWord.start < progressMs && progressMs < currentWord.end && currentWord.end > currentWord.start) {
            // 当前字进度比例 (0~1)
            val wordProgress = ((progressMs - currentWord.start).toFloat() / (currentWord.end - currentWord.start).toFloat()).coerceIn(0f, 1f)
            totalChars += wordProgress * currentWord.text.length
        }
    }
    
    return totalChars
}
