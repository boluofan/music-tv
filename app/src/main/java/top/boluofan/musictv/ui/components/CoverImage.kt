package top.boluofan.musictv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import top.boluofan.musictv.data.api.UrlHelper

/** 统一封面加载：相对路径解析为绝对 URL，加载中/失败/无封面时显示占位图标 */
@Composable
fun CoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: ImageVector = Icons.Rounded.MusicNote,
    placeholderTint: Color = Color.White.copy(alpha = 0.35f)
) {
    val resolved = UrlHelper.resolve(url)
    var showPlaceholder by remember(resolved) { mutableStateOf(true) }

    Box(modifier, contentAlignment = Alignment.Center) {
        if (showPlaceholder) {
            Icon(
                imageVector = placeholder,
                contentDescription = null,
                tint = placeholderTint,
                modifier = Modifier.fillMaxSize(0.4f)
            )
        }
        if (resolved != null) {
            AsyncImage(
                model = resolved,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onState = { state ->
                    showPlaceholder = state !is AsyncImagePainter.State.Success
                }
            )
        }
    }
}
