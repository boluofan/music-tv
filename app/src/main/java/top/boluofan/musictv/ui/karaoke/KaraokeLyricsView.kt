package top.boluofan.musictv.ui.karaoke

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import top.boluofan.musictv.data.model.LyricLine
import top.boluofan.musictv.ui.theme.PlayerColors
import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * KTV 双行卡拉 OK 歌词视图
 *
 * 核心特性：
 * - 固定显示两行：当前正在唱的行 + 下一行预览
 * - 滚动窗口机制避免整组替换跳动
 * - 50ms 高频刷新平滑过渡
 * - 幂函数 pacing 模拟前快后慢节奏
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KaraokeLyricsView(
    lyrics: List<LyricLine>?,
    progressMs: Long,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (lyrics == null || lyrics.isEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "暂无歌词",
                fontSize = 20.sp,
                color = Color.Gray
            )
        }
        return
    }

    // 平滑进度：本地时钟插值（50ms 刷新）
    var lyricTickMs by remember(lyrics) { mutableLongStateOf(progressMs) }

    LaunchedEffect(progressMs, isPlaying, lyrics) {
        if (isPlaying) {
            var anchorProgress = progressMs
            var anchorSystemMs = System.currentTimeMillis()
            lyricTickMs = anchorProgress

            while (isPlaying) {
                delay(50)
                val elapsed = System.currentTimeMillis() - anchorSystemMs
                lyricTickMs = anchorProgress + elapsed

                if (progressMs != anchorProgress) {
                    anchorProgress = progressMs
                    anchorSystemMs = System.currentTimeMillis()
                }
            }
        } else {
            lyricTickMs = progressMs
        }
    }

    // 找到当前歌词行索引
    val currentIndex = lyrics
        .indexOfFirst { it.time > lyricTickMs }
        .let { if (it == -1) lyrics.size - 1 else it - 1 }
        .coerceAtLeast(0)

    // 双行滚动窗口
    val onTopIsCurrent = currentIndex % 2 == 0
    val topLineIndex = if (onTopIsCurrent) currentIndex else currentIndex + 1
    val bottomLineIndex = if (onTopIsCurrent) currentIndex + 1 else currentIndex

    val topLine = lyrics.getOrNull(topLineIndex)
    val bottomLine = lyrics.getOrNull(bottomLineIndex)

    // 当前行结束时间
    val currentLineEndMs = lyrics.getOrNull(currentIndex + 1)?.time
        ?: (lyrics[currentIndex].time + 3000L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Bottom
    ) {
        // 顶部槽位：当前行，头部靠左（经典卡拉 OK 样式）
        topLine?.let { line ->
            val progress = if (onTopIsCurrent) {
                lineProgress(line.time, currentLineEndMs, lyricTickMs)
            } else 0f

            KaraokeLineText(
                text = line.text,
                progress = progress,
                fontSize = 36.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))

        // 底部槽位：下一行预览，尾部靠右
        bottomLine?.let { line ->
            val progress = if (!onTopIsCurrent) {
                lineProgress(line.time, currentLineEndMs, lyricTickMs)
            } else 0f

            KaraokeLineText(
                text = line.text,
                progress = progress,
                fontSize = 36.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// 幂函数指数：模拟"前快后慢"节奏
private const val KARAOKE_PACING_EXPONENT = 0.6f

internal fun karaokePacingFraction(progress: Float): Float {
    if (progress <= 0f) return 0f
    if (progress >= 1f) return 1f
    return progress.pow(KARAOKE_PACING_EXPONENT)
}

internal fun lineProgress(lineStartMs: Long, lineEndMs: Long, currentMs: Long): Float {
    if (lineEndMs <= lineStartMs) return 0f
    return ((currentMs - lineStartMs).toFloat() / (lineEndMs - lineStartMs).toFloat())
        .coerceIn(0f, 1f)
}

/**
 * 单行卡拉 OK 渲染：双层叠加实现平滑进度裁剪
 * - 底层：白色/灰色（未播放部分）
 * - 顶层：黄色（按进度裁剪揭示）
 */
@Composable
internal fun KaraokeLineText(
    text: String,
    progress: Float,
    fontSize: TextUnit = 36.sp,
    textAlign: TextAlign = TextAlign.End,
    baseColor: Color = PlayerColors.TextPrimary,
    highlightColor: Color = Color(0xFFFFD700),
    modifier: Modifier = Modifier
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = modifier) {
        // 底色层
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = textAlign,
            color = baseColor,
            onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth()
        )

        // 顶层层：按进度裁剪
        val lr = layout
        if (lr != null && progress > 0f) {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = textAlign,
                color = highlightColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        val contentScope = this
                        val coveredChars = karaokePacingFraction(progress) * text.length
                        var remaining = coveredChars

                        for (line in 0 until lr.lineCount) {
                            val lineStart = lr.getLineStart(line)
                            val lineEnd = lr.getLineEnd(line)
                            val lineLen = lineEnd - lineStart
                            if (lineLen <= 0) continue
                            if (remaining <= 0f) break

                            val take = minOf(remaining, lineLen.toFloat())
                            val boundaryX = if (take >= lineLen) {
                                lr.getLineRight(line)
                            } else {
                                val base = take.toInt()
                                val frac = take - base
                                val boundaryOffset = lineStart + base
                                val x1 = lr.getHorizontalPosition(boundaryOffset, usePrimaryDirection = true)
                                val x2 = if (boundaryOffset + 1 >= lineEnd) {
                                    lr.getLineRight(line)
                                } else {
                                    lr.getHorizontalPosition(boundaryOffset + 1, usePrimaryDirection = true)
                                }
                                x1 + (x2 - x1) * frac
                            }

                            clipRect(
                                left = lr.getLineLeft(line),
                                top = lr.getLineTop(line),
                                right = boundaryX,
                                bottom = lr.getLineBottom(line)
                            ) {
                                contentScope.drawContent()
                            }
                            remaining -= take
                        }
                    }
            )
        }
    }
}
