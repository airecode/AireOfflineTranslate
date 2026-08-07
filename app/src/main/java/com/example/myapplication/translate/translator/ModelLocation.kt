package com.example.myapplication.translate.translator

import android.content.Context
import java.io.File

/**
 * Where model weights live on disk, per [ModelVariant].
 *
 * Nothing is ever bundled: the smallest build is 2.59 GB, far past Google Play's base-module
 * limit, so every variant is fetched on demand and can be deleted again.
 */
object ModelLocation {

    private const val SUBDIR = "models"

    /** Partial downloads land here so a half-written file is never mistaken for a usable model. */
    private const val PART_SUFFIX = ".part"

    /**
     * A file smaller than this is treated as incomplete. Deliberately a fraction of the expected
     * size rather than an exact match, so a future re-publish of the weights does not brick an
     * installed app.
     */
    private const val MIN_COMPLETE_FRACTION = 0.95

    fun modelFile(context: Context, variant: ModelVariant): File =
        File(modelDir(context), variant.fileName)

    fun partFile(context: Context, variant: ModelVariant): File =
        File(modelDir(context), variant.fileName + PART_SUFFIX)

    /** Path relative to the app's external files dir, as DownloadManager wants it. */
    fun partRelativePath(variant: ModelVariant): String =
        "$SUBDIR/${variant.fileName}$PART_SUFFIX"

    fun isInstalled(context: Context, variant: ModelVariant): Boolean =
        modelFile(context, variant).let {
            it.isFile && it.length() >= variant.sizeBytes * MIN_COMPLETE_FRACTION
        }

    /** Removes the weights and any stray partial. Returns true if anything was deleted. */
    fun delete(context: Context, variant: ModelVariant): Boolean {
        val model = modelFile(context, variant).delete()
        val part = partFile(context, variant).delete()
        return model || part
    }

    fun installedBytes(context: Context, variant: ModelVariant): Long =
        modelFile(context, variant).let { if (it.isFile) it.length() else 0L }

    private fun modelDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, SUBDIR).apply { mkdirs() }
}
