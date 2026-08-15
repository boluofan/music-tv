package top.boluofan.musictv.util

import android.content.Context
import java.io.File

object LogStore {
    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "logs").apply { mkdirs() }
}
