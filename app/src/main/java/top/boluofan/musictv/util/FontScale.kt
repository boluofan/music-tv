package top.boluofan.musictv.util

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** 全局字体缩放比例，由 TvTheme 通过 CompositionLocal 提供 */
val LocalFontScale = compositionLocalOf { 1f }

object FontScalePreset {
    val Values = listOf("小", "中", "大", "特大")
    fun scaleForIndex(index: Int): Float = when (index) {
        0 -> 0.85f   // 小
        1 -> 1.0f    // 中（默认）
        2 -> 1.2f    // 大
        else -> 1.35f // 特大
    }
}

/** 带全局字体缩放的 Text 组件。替代原生 Material3 Text。 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    fontSize: TextUnit? = null,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit? = null,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle? = null
) {
    val scale = LocalFontScale.current
    val baseFontSize = fontSize ?: style?.fontSize ?: 14.sp
    val resolvedFontSize = baseFontSize * scale
    val resolvedLineHeight = lineHeight?.takeIf { it.value > 0 }?.let { it * scale }
    val resolvedColor = color ?: style?.color ?: LocalContentColor.current

    Text(
        text = text,
        modifier = modifier,
        fontSize = resolvedFontSize,
        lineHeight = resolvedLineHeight ?: TextUnit.Unspecified,
        color = resolvedColor,
        fontStyle = fontStyle ?: style?.fontStyle,
        fontWeight = fontWeight ?: style?.fontWeight,
        fontFamily = fontFamily ?: style?.fontFamily,
        letterSpacing = letterSpacing ?: style?.letterSpacing ?: TextUnit.Unspecified,
        textDecoration = textDecoration ?: style?.textDecoration,
        textAlign = textAlign ?: style?.textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style ?: TextStyle.Default
    )
}
