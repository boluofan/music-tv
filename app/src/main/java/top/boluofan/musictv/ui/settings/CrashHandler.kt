package top.boluofan.musictv.ui.settings

import android.app.Application
import android.os.Process
import android.util.Log
import java.lang.Thread.UncaughtExceptionHandler

private const val TAG = "CrashHandler"

class CrashHandler(private val context: Application) : UncaughtExceptionHandler {

    private val delegate = Thread.getDefaultUncaughtExceptionHandler()!!

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        Log.e(TAG, "Capturing crash on thread: ${thread.name}", ex)
        CrashReporter.handleCrash(context, ex)

        try { delegate.uncaughtException(thread, ex) } catch (_: Exception) {}
        try { Thread.sleep(1000L) } catch (_: InterruptedException) {}
        Process.killProcess(Process.myPid())
        System.exit(10)
    }
}
