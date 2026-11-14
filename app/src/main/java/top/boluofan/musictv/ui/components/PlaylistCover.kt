package top.boluofan.musictv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import top.boluofan.musictv.data.api.UrlHelper
import top.boluofan.musictv.data.model.Playlist

/**
 * 歌单封面三级降级（首页歌单卡片、歌单广场、搜索结果、详情页共用）：
 * 1. 歌单自带封面 → 正常加载；加载失败也继续往下走，不留空白
 * 2. 没有自带封面 → 用 [songCovers]（歌单内抽出的歌曲封面）拼贴
 * 3. 歌曲也都没有封面 → 显示内置曲库封面 [BuiltinLibraryCover]
 *
 * 只有真正需要降级时才通过 [onNeedSongCovers] 触发按需采样。这里刻意只用
 * [Playlist.customCoverUrl]，不把「第一首歌的封面」算作歌单封面，否则无封面的
 * 歌单永远不会走到拼贴这一级。
 */
@Composable
fun PlaylistCover(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    songCovers: List<String> = emptyList(),
    onNeedSongCovers: ((Playlist) -> Unit)? = null
) {
    val coverUrl = playlist.customCoverUrl
    var mainFailed by remember(coverUrl) { mutableStateOf(false) }
    val needFallback = coverUrl == null || mainFailed

    LaunchedEffect(playlist.id, needFallback) {
        if (needFallback) onNeedSongCovers?.invoke(playlist)
    }

    val fallback: @Composable () -> Unit = {
        if (songCovers.isEmpty()) {
            BuiltinLibraryCover(Modifier.fillMaxSize())
        } else {
            CoverCollage(songCovers, Modifier.fillMaxSize())
        }
    }

    Box(modifier = modifier) {
        if (coverUrl != null && !mainFailed) {
            RemoteCover(
                url = coverUrl,
                modifier = Modifier.fillMaxSize(),
                fallback = fallback,
                onError = { mainFailed = true }
            )
        } else {
            fallback()
        }
    }
}

/** 单张远程封面：加载中/失败显示 [fallback]，失败时通过 [onError] 通知上层降级 */
@Composable
private fun RemoteCover(
    url: String,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
    onError: (() -> Unit)? = null
) {
    val resolved = remember(url) { UrlHelper.resolve(url) }
    var loaded by remember(resolved) { mutableStateOf(false) }
    var failed by remember(resolved) { mutableStateOf(false) }

    // 服务器地址未配置时 URL 无法解析，同样按「封面不可用」走降级链
    LaunchedEffect(resolved) {
        if (resolved == null) onError?.invoke()
    }

    Box(modifier = modifier) {
        if (!loaded) fallback()
        if (resolved != null && !failed) {
            AsyncImage(
                model = resolved,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> loaded = true
                        is AsyncImagePainter.State.Error -> {
                            failed = true
                            onError?.invoke()
                        }
                        else -> Unit
                    }
                }
            )
        }
    }
}

/**
 * 拼贴布局：4 张走 2×2 田字格（视觉密度最接近单张封面），3 张走左大右两小，
 * 2 张左右对半、1 张全幅。最后叠一层轻薄主色蒙版统一色调，角标也能保持可读。
 */
@Composable
private fun CoverCollage(urls: List<String>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier = modifier) {
        when (urls.size) {
            1 -> CollageTile(urls[0], Modifier.fillMaxSize())
            2 -> Row(Modifier.fillMaxSize()) {
                CollageTile(urls[0], Modifier.weight(1f).fillMaxHeight())
                CollageTile(urls[1], Modifier.weight(1f).fillMaxHeight())
            }
            3 -> Row(Modifier.fillMaxSize()) {
                CollageTile(urls[0], Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    CollageTile(urls[1], Modifier.weight(1f).fillMaxWidth())
                    CollageTile(urls[2], Modifier.weight(1f).fillMaxWidth())
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    CollageTile(urls[0], Modifier.weight(1f).fillMaxHeight())
                    CollageTile(urls[1], Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    CollageTile(urls[2], Modifier.weight(1f).fillMaxHeight())
                    CollageTile(urls[3], Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        Box(Modifier.fillMaxSize().background(primary.copy(alpha = 0.10f)))
    }
}

@Composable
private fun CollageTile(url: String, modifier: Modifier = Modifier) {
    RemoteCover(
        url = url,
        modifier = modifier,
        fallback = { BuiltinLibraryCover(Modifier.fillMaxSize(), compact = true) }
    )
}

/**
 * 内置曲库封面：随主题主色变化的渐变 + 黑胶唱片环 + 音符标识。
 * 纯代码绘制，无需图片资源，任意尺寸清晰；compact 用于拼贴中单张图失败的小格子。
 */
@Composable
fun BuiltinLibraryCover(modifier: Modifier = Modifier, compact: Boolean = false) {
    val primary = MaterialTheme.colorScheme.primary
    val deep = Color(0xFF101A30)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val min = size.minDimension
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        blend(primary, deep, 0.62f),
                        blend(primary, deep, 0.30f),
                        primary
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            if (!compact) {
                val glowCenter = Offset(size.width * 0.78f, size.height * 0.20f)
                val glowRadius = min * 0.62f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        center = glowCenter,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = glowCenter
                )
                val discCenter = Offset(size.width * 0.74f, size.height * 0.72f)
                val discRadius = min * 0.34f
                val ringColor = Color.White.copy(alpha = 0.15f)
                for (scale in listOf(1f, 0.79f, 0.58f, 0.37f)) {
                    drawCircle(
                        color = ringColor,
                        radius = discRadius * scale,
                        center = discCenter,
                        style = Stroke(width = min * 0.012f)
                    )
                }
                drawCircle(
                    color = Color.White.copy(alpha = 0.26f),
                    radius = discRadius * 0.16f,
                    center = discCenter
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.65f),
                    radius = discRadius * 0.05f,
                    center = discCenter
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.LibraryMusic,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.fillMaxSize(if (compact) 0.40f else 0.30f)
        )
    }
}

private fun blend(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction
)
