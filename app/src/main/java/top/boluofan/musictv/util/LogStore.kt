package top.boluofan.musictv.util

import android.content.Context
import java.io.File

object LogStore {
    /**
     * 日志导出目录：优先外部应用专属目录，失败回退应用内部存储。
     *
     * 部分电视固件 `getExternalFilesDir()` 会返回非 null 的路径，但目录实际无法创建/写入，
     * 此时仅判空会导致导出时报 ENOENT，因此用临时文件实测可写性。
     */
    fun dir(context: Context): File =
        writableDir(context, "logs", context.getExternalFilesDir(null))

    /**
     * 通用可写目录探测：依次尝试 `externalDir/child` 与 `filesDir/child`，
     * 返回第一个真正可创建目录并能写入文件的候选。
     *
     * 回退到内部目录时，分享给外部应用需配合 FileProvider（见 res/xml/file_paths.xml）。
     */
    fun writableDir(context: Context, child: String, externalDir: File?): File {
        val candidates = listOfNotNull(
            externalDir?.let { File(it, child) },
            File(context.filesDir, child)
        )
        for (candidate in candidates) {
            if (candidate.ensureWritable()) return candidate
        }
        // 两者都不可写时仍返回内部目录，让上层拿到明确异常而不是崩溃在目录判空上
        return candidates.last().apply { mkdirs() }
    }

    private fun File.ensureWritable(): Boolean = runCatching {
        if (!isDirectory && !mkdirs()) return@runCatching false
        if (!isDirectory) return@runCatching false
        val probe = File(this, ".probe")
        probe.writeText("ok")
        probe.delete()
        true
    }.getOrDefault(false)
}
