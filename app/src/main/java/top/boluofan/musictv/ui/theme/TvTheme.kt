package top.boluofan.musictv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map
import top.boluofan.musictv.data.storage.PreferencesDataStore
import top.boluofan.musictv.data.storage.dataStore

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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = TvShapes,
        content = content
    )
}
