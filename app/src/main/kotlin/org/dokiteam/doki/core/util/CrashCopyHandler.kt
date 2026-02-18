package org.dokiteam.doki.core.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import org.dokiteam.doki.R
import java.io.PrintWriter
import java.io.StringWriter

class CrashCopyHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler? = null
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val errorReport = generateErrorReport(throwable)

        (context.applicationContext as? android.app.Application)?.let { app ->
            val dialog = AlertDialog.Builder(app)
                .setTitle(R.string.error_occurred)
                .setMessage(context.getString(R.string.crash_text) + "\n\n" + errorReport)
                .setPositiveButton(R.string.copy_error) { _, _ ->
                    copyErrorToClipboard(errorReport)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener {
                    defaultHandler?.uncaughtException(thread, throwable)
                }
                .create()

            if (Looper.myLooper() == Looper.getMainLooper()) {
                dialog.show()
            } else {
                app.mainLooper.run {
                    dialog.show()
                }
            }
        }
    }

    private fun generateErrorReport(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
        
        return buildString {
            appendLine("App Version: $appVersion")
            appendLine("Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Crash Time: ${java.time.LocalDateTime.now()}")
            appendLine()
            appendLine("Stack Trace:")
            appendLine(sw.toString())
        }
    }

    private fun copyErrorToClipboard(errorReport: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Doki Error Report", errorReport)
        clipboard.setPrimaryClip(clip)
    }
}