package top.boluofan.musictv.util

import android.content.Context
import java.io.File

object LogStore {
    /**
     * 日志目录：优先使用外部应用专属目录；部分电视固件上该目录无法创建或写入
     * （导出时报 ENOENT），此时自动回退到应用内部存储。
     */
    fun dir(context: Context): File {
        val candidates = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, "logs") },
            File(context.filesDir, "logs")
        )
        for (candidate in candidates) {
            if (isWritableDir(candidate)) return candidate
        }
        return candidates.last().apply { mkdirs() }
    }

    private fun isWritableDir(dir: File): Boolean = runCatching {
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) return false
        val probe = File(dir, ".probe")
        probe.writeText("ok")
        probe.delete()
        true
    }.getOrDefault(false)
}
