package com.example.myapplication.translate.translator

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.myapplication.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fetches a [ModelVariant] on demand.
 *
 * Uses the platform [DownloadManager] rather than a plain HTTP call: a multi-gigabyte transfer
 * needs to survive the app being backgrounded, the screen locking, and Wi-Fi dropping out, and
 * this gets all three plus resume and a system progress notification for free.
 *
 * Download state is tracked per variant, so switching models mid-download does not confuse one
 * transfer for another.
 */
class ModelDownloader(context: Context) {

    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    sealed interface State {
        /** No model, and no download running. */
        data object Absent : State

        data class InProgress(val bytesDone: Long, val bytesTotal: Long) : State {
            val fraction: Float
                get() = if (bytesTotal > 0) (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f
        }

        data object Installed : State

        data class Failed(val reason: String) : State
    }

    fun isInstalled(variant: ModelVariant): Boolean = ModelLocation.isInstalled(appContext, variant)

    fun isDownloading(variant: ModelVariant): Boolean = activeDownloadId(variant) != null

    /** Starts from the first source, or does nothing if this variant is already in flight. */
    fun start(variant: ModelVariant) {
        if (isInstalled(variant) || activeDownloadId(variant) != null) return
        setSourceIndex(variant, 0)
        launchCurrentSource(variant)
    }

    /**
     * Abandons the current source and starts the next one.
     *
     * Returns false when every source has been tried. Progress restarts from zero rather than
     * resuming: the sources are distinct URLs and a byte range from one is not valid against
     * another, so continuing would corrupt the file.
     */
    private fun advanceSource(variant: ModelVariant): Boolean {
        val next = sourceIndex(variant) + 1
        activeDownloadId(variant)?.let { runCatching { downloadManager.remove(it) } }
        clearDownloadId(variant)
        ModelLocation.partFile(appContext, variant).delete()

        if (next >= variant.downloadUrls.size) {
            Log.e(TAG, "All ${variant.downloadUrls.size} sources failed for ${variant.id}")
            return false
        }
        Log.w(TAG, "Switching ${variant.id} to source ${next + 1}/${variant.downloadUrls.size}")
        setSourceIndex(variant, next)
        launchCurrentSource(variant)
        return true
    }

    private fun launchCurrentSource(variant: ModelVariant) {
        ModelLocation.partFile(appContext, variant).delete()

        val url = variant.downloadUrls[sourceIndex(variant)]
        Log.i(TAG, "Downloading ${variant.id} from source ${sourceIndex(variant) + 1}: $url")

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(variant.displayName)
            .setDescription(appContext.getString(R.string.notif_downloading_model))
            .setDestinationInExternalFilesDir(
                appContext, null, ModelLocation.partRelativePath(variant)
            )
            .setAllowedOverMetered(false) // Multi-GB — never on mobile data without asking.
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val id = try {
            downloadManager.enqueue(request)
        } catch (t: Throwable) {
            Log.e(TAG, "Could not enqueue download for ${variant.id}", t)
            return
        }
        prefs.edit().putLong(keyDownloadId(variant), id).apply()
    }

    fun cancel(variant: ModelVariant) {
        activeDownloadId(variant)?.let { downloadManager.remove(it) }
        prefs.edit().remove(keyDownloadId(variant)).remove(keySourceIndex(variant)).apply()
        ModelLocation.partFile(appContext, variant).delete()
    }

    /** Cancels any transfer then removes the weights from disk. */
    fun delete(variant: ModelVariant) {
        cancel(variant)
        ModelLocation.delete(appContext, variant)
    }

    /** Polls until the variant is installed, or every source has failed. */
    fun observe(variant: ModelVariant): Flow<State> = flow {
        var lastBytes = -1L
        var lastProgressAt = System.currentTimeMillis()

        while (true) {
            var state = currentState(variant)

            // DownloadManager reports no error for a connection that simply stops delivering
            // bytes, so a stall has to be detected here or the UI sits on a frozen progress bar
            // indefinitely. Treat it as a timeout and move to the next source.
            if (state is State.InProgress) {
                if (state.bytesDone > lastBytes) {
                    lastBytes = state.bytesDone
                    lastProgressAt = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - lastProgressAt > STALL_TIMEOUT_MS) {
                    Log.w(TAG, "No progress for ${STALL_TIMEOUT_MS / 1000}s; switching source")
                    state = if (advanceSource(variant)) {
                        State.InProgress(0, variant.sizeBytes)
                    } else {
                        State.Failed(appContext.getString(R.string.err_download_all_sources))
                    }
                    lastBytes = -1L
                    lastProgressAt = System.currentTimeMillis()
                }
            }

            emit(state)
            if (state is State.Installed || state is State.Failed) return@flow
            delay(POLL_INTERVAL_MS)
        }
    }

    fun currentState(variant: ModelVariant): State {
        if (isInstalled(variant)) return State.Installed

        val id = activeDownloadId(variant) ?: return State.Absent

        downloadManager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) {
                clearDownloadId(variant)
                return State.Absent
            }

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            ).takeIf { it > 0 } ?: variant.sizeBytes

            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    clearDownloadId(variant)
                    if (promotePartFile(variant)) {
                        State.Installed
                    } else {
                        State.Failed(appContext.getString(R.string.err_download_save))
                    }
                }

                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                    )
                    Log.w(TAG, "Source ${sourceIndex(variant) + 1} failed: ${describeFailure(reason)}")
                    // Try the next source before surfacing anything: one unreachable host should
                    // not end a multi-gigabyte download the user has been waiting on.
                    if (advanceSource(variant)) {
                        State.InProgress(0, variant.sizeBytes)
                    } else {
                        State.Failed(describeFailure(reason))
                    }
                }

                else -> State.InProgress(done, total)
            }
        }

        clearDownloadId(variant)
        return State.Absent
    }

    /**
     * Renames `.part` to the real filename. Downloading straight to the final path would let a
     * half-finished file look like an installed model to [ModelLocation.isInstalled].
     */
    private fun promotePartFile(variant: ModelVariant): Boolean {
        val part = ModelLocation.partFile(appContext, variant)
        val target = ModelLocation.modelFile(appContext, variant)
        if (!part.isFile) return target.isFile
        target.delete()
        return part.renameTo(target)
    }

    private fun keyDownloadId(variant: ModelVariant) = "download_id_${variant.id}"
    private fun keySourceIndex(variant: ModelVariant) = "source_index_${variant.id}"

    private fun activeDownloadId(variant: ModelVariant): Long? =
        prefs.getLong(keyDownloadId(variant), -1L).takeIf { it >= 0 }

    private fun clearDownloadId(variant: ModelVariant) {
        prefs.edit().remove(keyDownloadId(variant)).apply()
    }

    private fun sourceIndex(variant: ModelVariant): Int =
        prefs.getInt(keySourceIndex(variant), 0).coerceIn(0, variant.downloadUrls.lastIndex)

    private fun setSourceIndex(variant: ModelVariant, index: Int) {
        prefs.edit().putInt(keySourceIndex(variant), index).apply()
    }

    private fun describeFailure(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> appContext.getString(R.string.err_download_space)
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> appContext.getString(R.string.err_download_storage)
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
            appContext.getString(R.string.err_download_network)
        DownloadManager.ERROR_CANNOT_RESUME -> appContext.getString(R.string.err_download_resume)
        DownloadManager.ERROR_FILE_ERROR -> appContext.getString(R.string.err_download_file)
        else -> appContext.getString(R.string.err_download_generic, reason)
    }

    private companion object {
        const val TAG = "ModelDownloader"
        const val PREFS = "model_download"
        const val POLL_INTERVAL_MS = 1_000L

        /** No bytes for this long counts as a dead source rather than a slow one. */
        const val STALL_TIMEOUT_MS = 45_000L
    }
}
