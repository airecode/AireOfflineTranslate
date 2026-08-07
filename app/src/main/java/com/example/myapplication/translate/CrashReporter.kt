package com.example.myapplication.translate

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists uncaught exceptions so the next launch can show what killed the previous one.
 *
 * On-device failures are otherwise near-impossible to diagnose without adb attached, and "it
 * crashed and exited" is not enough to act on. This turns the crash into a stack trace visible in
 * the app itself.
 *
 * Only JVM crashes are captured. A native SIGSEGV inside the LiteRT runtime, or the process being
 * killed for memory, will leave no report — which is itself diagnostic: nothing recorded means the
 * fault was below the JVM.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(appContext.filesDir, FILE_NAME).writeText(
                    "$timestamp\nthread=${thread.name}\n${Log.getStackTraceString(throwable)}"
                )
            }.onFailure { Log.w(TAG, "Could not persist crash report", it) }

            // Still let the platform do its normal thing, so the crash dialog and logcat entry
            // appear as usual.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Returns the previous crash report, if any, and clears it. */
    fun consume(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull()
        file.delete()
        return text?.takeIf { it.isNotBlank() }
    }

    /** First meaningful line of a report, for showing in a one-line status strip. */
    fun summarise(report: String): String {
        val exceptionLine = report.lineSequence()
            .firstOrNull { it.contains("Exception") || it.contains("Error") }
            ?.trim()
        return exceptionLine?.take(120) ?: report.lineSequence().take(2).joinToString(" ").take(120)
    }
}
