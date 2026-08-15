package top.boluofan.musictv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object TvFocusDefaults {
    val scaleIn = 1.05f
    val scaleOut = 1.0f
    val shadowElevationIn = 8.dp
    val shadowElevationOut = 0.dp
    val borderWidth = 2.dp
    val animationDuration = 150
    val cornerRadius = 12.dp
}

fun Modifier.tvFocusable(
    scaleIn: Float = TvFocusDefaults.scaleIn,
    shadowElevationIn: Dp = TvFocusDefaults.shadowElevationIn,
    cornerRadius: Dp = TvFocusDefaults.cornerRadius,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null
): Modifier = composed(
    factory = {
        val resolvedBorderColor = borderColor ?: MaterialTheme.colorScheme.primary
        var isFocused by remember { mutableStateOf(false) }

        val scale by animateFloatAsState(
            targetValue = if (isFocused) scaleIn else 1.0f,
            animationSpec = tween(TvFocusDefaults.animationDuration),
            label = "tvFocusScale"
        )

        val elevation by animateDpAsState(
            targetValue = if (isFocused) shadowElevationIn else 0.dp,
            animationSpec = tween(TvFocusDefaults.animationDuration),
            label = "tvFocusElevation"
        )

        val shape = RoundedCornerShape(cornerRadius)

        this
            .scale(scale)
            .shadow(elevation, shape)
            .then(
                if (isFocused) Modifier.border(
                    width = TvFocusDefaults.borderWidth,
                    color = resolvedBorderColor,
                    shape = shape
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier.focusable()
            )
    }
)
