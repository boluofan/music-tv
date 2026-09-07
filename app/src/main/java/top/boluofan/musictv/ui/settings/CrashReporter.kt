package top.boluofan.musictv.ui.settings

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "CrashReporter"
private const val CRASH_DIR_NAME = "crash"
private const val FILE_PREFIX = "crash_"
private const val FILE_SUFFIX = ".log"
private const val MAX_CRASH_FILES = 5

object CrashReporter {

    fun handleCrash(context: Context, throwable: Throwable) {
        val crashDir = getCrashDir(context) ?: return
        try {
            rotate(crashDir)
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(crashDir, "${FILE_PREFIX}$timestamp$FILE_SUFFIX")
            PrintWriter(file.writer()).use { pw ->
                throwable.printStackTrace(pw)
                val traces = Thread.currentThread().stackTrace
                pw.appendLine("\n--- Stack trace of main thread (${traces.size} frames) ---")
                for (ste in traces) pw.appendLine("\tat $ste")
            }
            Log.d(TAG, "Crash log saved: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log", e)
        }
    }

    fun getRecentCrashes(context: Context): List<File> {
        val crashDir = getCrashDir(context) ?: return emptyList()
        return crashDir.listFiles { f ->
            f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX)
        }?.sortedByDescending { it.lastModified() }.orEmpty()
    }

    fun loadCrashContent(file: File): String? = runCatching { file.readText() }.getOrNull()

    fun clearCrashes(context: Context): Int {
        val crashDir = getCrashDir(context) ?: return 0
        var count = 0
        crashDir.listFiles { f ->
            f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX)
        }?.forEach { if (it.delete()) count++ }
        return count
    }

    fun createShareIntent(context: Context, content: String, filename: String): android.content.Intent? {
        return try {
            val externalDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: return null
            val outFile = File(externalDir, filename)
            outFile.writeText(content)
            val fileUri = android.net.Uri.fromFile(outFile)
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Music TV 崩溃日志")
                putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create share intent", e)
            null
        }
    }

    fun copyToClipboard(context: Context, content: String) {
        try {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Music TV 崩溃日志", content))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to clipboard", e)
        }
    }

    private fun getCrashDir(context: Context): File? {
        val dir = File(context.filesDir, CRASH_DIR_NAME)
        return if (dir.isDirectory || dir.mkdirs()) dir else null
    }

    private fun rotate(crashDir: File) {
        val files = crashDir.listFiles { f ->
            f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX)
        }?.sortedBy { it.lastModified() }.orEmpty()
        while (files.size >= MAX_CRASH_FILES) {
            files.first().delete()
            break
        }
    }
}
