package top.boluofan.musictv.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.boluofan.musictv.data.model.LyricLine
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

    // 当前句字号由设置决定，其余行按默认比例（30/42/22/30/16）派生
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
                    KaraokeLine(
                        line = line,
                        position = currentPosition,
                        highlightColor = highlightColor,
                        fontSize = fontSize
                    )
                } else {
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

/** 当前行逐字渐进高亮：已唱过的字为高亮色，正在唱的字按进度部分点亮。 */
@Composable
private fun KaraokeLine(line: LyricLine, position: Long, highlightColor: Color, fontSize: Int = 30) {
    val words = line.words ?: return
    val spanActive = SpanStyle(color = highlightColor)
    val spanInactive = SpanInactive
    val annotated = buildAnnotatedString {
        for (word in words) {
            val lit = when {
                position >= word.end -> 1f
                position <= word.start -> 0f
                word.end > word.start ->
                    ((position - word.start).toFloat() / (word.end - word.start)).coerceIn(0f, 1f)
                else -> 0f
            }
            val litChars = (word.text.length * lit).toInt().coerceIn(0, word.text.length)
            if (litChars > 0) {
                withStyle(spanActive) { append(word.text.substring(0, litChars)) }
            }
            if (litChars < word.text.length) {
                withStyle(spanInactive) { append(word.text.substring(litChars)) }
            }
        }
    }
    Text(
        text = annotated,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 42 / 30).sp,
        fontWeight = FontWeight.Bold,
        style = TextStyle(shadow = PlayerColors.LyricShadow),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
    )
}

private val SpanInactive = SpanStyle(color = PlayerColors.LyricsInactive)
