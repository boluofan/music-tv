package top.boluofan.musictv.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.boluofan.musictv.data.api.UrlHelper
import top.boluofan.musictv.ui.theme.PlayerColors

/**
 * 自适应背景的封面图组件。
 *
 * 原理：
 * 1. AsyncImage 加载并渲染封面图（含 blur）
 * 2. LaunchedEffect 中通过 Coil ImageLoader 同步解码图片到 Drawable → Bitmap 采样亮度
 *    （由于 Coil 内存缓存，第二次请求走本地无网络开销）
 * 3. 将亮度归一化到 [0, 1]，映射到 PlayerColors.scrimForBrightness(brightness)
 *    - 亮色封面（brightness≈1）→ alpha ≈ 0.4（浅遮罩，歌词清晰）
 *    - 暗色封面（brightness≈0）→ alpha ≈ 0.9（深遮罩，保留氛围感）
 */
@Composable
fun AdaptiveCoverBackground(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    blurRadiusDp: Int = 60
) {
    var brightness by remember(url) { mutableFloatStateOf(0.5f) }
    val resolvedUrl = UrlHelper.resolve(url)
    val context = LocalContext.current

    // 图片加载后异步计算亮度
    LaunchedEffect(resolvedUrl) {
        if (resolvedUrl != null) {
            brightness = computeBrightness(context, resolvedUrl)
        }
    }

    Box(modifier = modifier) {
        AsyncImage(
            model = resolvedUrl,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().blur(blurRadiusDp.dp),
            contentScale = contentScale
        )

        // 自适应遮罩层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PlayerColors.scrimForBrightness(brightness))
        )
    }
}

/** 使用 Coil ImageLoader 下载图片并计算平均亮度，结果受 Coil 内存缓存加速 */
private suspend fun computeBrightness(
    context: android.content.Context,
    url: Any
): Float = withContext(Dispatchers.IO) {
    try {
        val imageLoader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
        val result = imageLoader.execute(request)
        when (result) {
            is SuccessResult -> {
                val bitmap = drawableToBitmap(result.drawable)
                if (bitmap != null) estimateAverageLuminance(bitmap) else 0.5f
            }
            else -> 0.5f
        }
    } catch (_: Exception) {
        0.5f
    }
}

/** 尝试从任意 Drawable 中提取或转换为 Bitmap */
private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    return try {
        when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            else -> {
                @Suppress("DEPRECATION")
                val size = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight, 1)
                val clampedSize = minOf(size.coerceAtMost(300), 800)
                val bitmap = Bitmap.createBitmap(clampedSize, clampedSize, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * 对 Bitmap 进行网格采样，计算 ITU-R BT.601 平均亮度。
 * 采样密度约 ~64 个点，兼顾精度与性能。
 */
private fun estimateAverageLuminance(bitmap: Bitmap): Float {
    val width = bitmap.width
    val height = bitmap.height
    val step = maxOf(1, minOf(width, height) / 8)
    var totalLuminance = 0f
    var count = 0

    @Suppress("DEPRECATION")
    for (y in 0 until height step step) {
        for (x in 0 until width step step) {
            bitmap.getPixel(x, y).let { pixel ->
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalLuminance += 0.299f * r + 0.587f * g + 0.114f * b
                count++
            }
        }
    }

    if (count == 0) return 0.5f
    val avgLuminance = totalLuminance / count // [0, 255]
    return (avgLuminance / 255f).coerceIn(0f, 1f)
}
