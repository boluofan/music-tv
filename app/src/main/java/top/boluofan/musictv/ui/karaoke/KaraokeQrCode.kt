package top.boluofan.musictv.ui.karaoke

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.boluofan.musictv.ui.components.generateQrBitmap
import top.boluofan.musictv.ui.theme.PlayerColors
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect

/**
 * 右上角"扫码点歌"二维码。
 *
 * 默认显示小尺寸二维码；点击（触屏）或遥控器选中后弹出大图供手机扫描。
 * 焦点框样式与主播放器按钮一致：聚焦时放大、描边 [PlayerColors.ControlBorder]。
 */
@Composable
fun KaraokeQrCode(
    url: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(url) { generateQrBitmap(url, size = 512) }
    var enlarged by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        animationSpec = tween(120),
        label = "qrScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) PlayerColors.ControlBgFocused else Color.Transparent)
            .border(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) PlayerColors.ControlBorder else Color.White.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { enlarged = true }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "扫码点歌二维码",
                modifier = Modifier.size(92.dp)
            )
        }
    }

    if (enlarged) {
        QrEnlargeDialog(bitmap = bitmap, url = url, onDismiss = { enlarged = false })
    }
}

@Composable
private fun QrEnlargeDialog(
    bitmap: Bitmap?,
    url: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(Unit) { /* 自动聚焦到对话框背景以便遥控器返回键关闭 */ }
        Box(
            modifier = Modifier
                .size(420.dp)
                .background(Color(0xFF111827), RoundedCornerShape(16.dp))
                .border(2.dp, PlayerColors.ControlBorder, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "扫码点歌二维码",
                        modifier = Modifier.size(360.dp)
                    )
                }
                Text(
                    text = "手机扫码点歌",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
