package top.boluofan.musictv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.map
import top.boluofan.musictv.data.storage.PreferencesDataStore
import top.boluofan.musictv.data.storage.dataStore
import top.boluofan.musictv.util.FontScalePreset
import top.boluofan.musictv.util.LocalFontScale

/** 将 Typography 中所有文本样式按 scale 缩放 */
fun scaledTypography(base: Typography = Typography(), scale: Float): Typography {
    fun s(textStyle: TextStyle): TextStyle {
        val baseFontSize = textStyle.fontSize.takeIf { it.value > 0f } ?: 14.sp
        return textStyle.copy(fontSize = (baseFontSize.value * scale).sp)
    }
    return Typography(
        displayLarge = s(base.displayLarge),
        displayMedium = s(base.displayMedium),
        displaySmall = s(base.displaySmall),
        headlineLarge = s(base.headlineLarge),
        headlineMedium = s(base.headlineMedium),
        headlineSmall = s(base.headlineSmall),
        titleLarge = s(base.titleLarge),
        titleMedium = s(base.titleMedium),
        titleSmall = s(base.titleSmall),
        bodyLarge = s(base.bodyLarge),
        bodyMedium = s(base.bodyMedium),
        bodySmall = s(base.bodySmall),
        labelLarge = s(base.labelLarge),
        labelMedium = s(base.labelMedium),
        labelSmall = s(base.labelSmall),
    )
}

private fun lightScheme(seed: Color) = lightColorScheme(
    primary = seed,
    onPrimary = Color.White,
    secondary = seed.copy(alpha = 0.8f),
    surface = Color(0xFFF5F5F5),
    onSurface = Color(0xFF1C1B1F),
    background = Color.White,
    onBackground = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
)

private fun darkScheme(seed: Color) = darkColorScheme(
    primary = seed,
    onPrimary = Color.White,
    secondary = seed.copy(alpha = 0.7f),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    background = Color(0xFF111827),
    onBackground = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFCAC4D0),
)

/** 暗夜模式：深色系变体，冷蓝紫暗调，强调沉浸感 */
private fun nightScheme(seed: Color) = darkColorScheme(
    primary = seed,
    onPrimary = Color.White,
    secondary = seed.copy(alpha = 0.7f),
    surface = Color(0xFF232D4C),
    onSurface = Color(0xFFE6E8F5),
    background = Color(0xFF1A2340),
    onBackground = Color(0xFFE6E8F5),
    surfaceVariant = Color(0xFF323C60),
    onSurfaceVariant = Color(0xFF9AA0C0),
)

val TvShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun TvTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeMode by remember {
        context.dataStore.data.map { it[PreferencesDataStore.THEME_MODE] ?: 0 }
    }.collectAsState(initial = 0)
    val themeColorName by remember {
        context.dataStore.data.map { it[PreferencesDataStore.THEME_COLOR] ?: ThemeSeeds.DEFAULT_NAME }
    }.collectAsState(initial = ThemeSeeds.DEFAULT_NAME)

    val seed = seedColorFor(themeColorName)
    val colorScheme = when (themeMode) {
        1 -> lightScheme(seed)
        2 -> darkScheme(seed)
        3 -> nightScheme(seed)
        else -> if (isSystemInDarkTheme()) darkScheme(seed) else lightScheme(seed)
    }

    // 全局字体缩放比例
    val fontSizeScaleIndex by remember {
        context.dataStore.data.map { it[PreferencesDataStore.FONT_SIZE_SCALE] ?: 1 }
    }.collectAsState(initial = 1)
    val fontScale = FontScalePreset.scaleForIndex(fontSizeScaleIndex)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography(Typography(), fontScale),
        shapes = TvShapes,
        content = {
            CompositionLocalProvider(LocalFontScale provides fontScale) {
                content()
            }
        }
    )
}
