package top.boluofan.musictv.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {
    private const val TAG = "ApkInstaller"

    /** 调起系统安装器，失败返回 false（由 UI 提示手动下载） */
    fun install(context: Context, file: File): Boolean {
        return try {
            context.startActivity(buildInstallIntent(context, file))
            true
        } catch (e: Exception) {
            Log.w(TAG, "调起安装器失败", e)
            // 26+ 未授权未知来源时部分系统直接拒绝，尝试引导授权页后再试一次
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls() &&
                openUnknownSourcesSettings(context)
            ) {
                return false
            }
            false
        }
    }

    private fun buildInstallIntent(context: Context, file: File): Intent {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun openUnknownSourcesSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: ActivityNotFoundException) {
            // 大量 TV 盒子没有该设置页
            Log.w(TAG, "无未知来源设置页", e)
            false
        }
    }
}
