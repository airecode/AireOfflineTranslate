package com.example.myapplication.translate

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.util.Log

/**
 * Reports why the previous process ended.
 *
 * A model that "unloads when the app is minimised" looks identical, from inside the app, to a
 * process Android killed while it was in the background: either way the next launch finds no engine
 * loaded. The two have opposite fixes, so guessing between them is worthless — and holding ~2.6 GB
 * makes this app by far the most attractive thing on the device for the low-memory killer to
 * reclaim.
 *
 * [ActivityManager.getHistoricalProcessExitReasons] is Android's own record of the answer.
 */
object ProcessExitLog {

    private const val TAG = "ProcessExitLog"

    /** Logs the most recent exit, if the system still has a record of one. */
    fun logLastExit(context: Context) {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return

        val last = runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
        }.onFailure { Log.w(TAG, "Could not read exit reasons", it) }
            .getOrNull() ?: return

        Log.i(
            TAG,
            "Previous process ended: ${describe(last.reason)} " +
                "(status=${last.status}, rss=${last.pss / 1024} MB pss, " +
                "importance=${last.importance}) — ${last.description}"
        )
    }

    private fun describe(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY (killed to reclaim RAM)"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED (swiped away)"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED (force stopped)"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        else -> "reason $reason"
    }
}
