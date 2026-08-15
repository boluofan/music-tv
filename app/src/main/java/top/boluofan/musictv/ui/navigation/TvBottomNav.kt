package top.boluofan.musictv.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

@Composable
fun TvBottomNav(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember { listOf(Screen.Home, Screen.Search, Screen.Discover, Screen.My) }
    val bridge = LocalTabBarBridge.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(horizontal = 48.dp)
            .onFocusChanged { bridge?.hasFocus = it.hasFocus },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { screen ->
            val isSelected = currentScreen::class == screen::class
            var isFocused by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue = if (isFocused) 1.1f else 1.0f,
                animationSpec = tween(150),
                label = "navScale"
            )

            Box(
                modifier = Modifier
                    .scale(scale)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (isSelected) Modifier.border(
                            2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
                        ) else Modifier
                    )
                    .then(
                        if (isFocused) Modifier.background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp)
                        ) else Modifier
                    )
                    .then(
                        if (isSelected && bridge != null) {
                            Modifier.focusRequester(bridge.tabFocusRequester)
                        } else Modifier
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickable { onScreenSelected(screen) }
                    .padding(horizontal = 32.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = screen.label,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isFocused -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    },
                    fontSize = 18.sp,
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
