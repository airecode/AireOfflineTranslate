package com.example.myapplication.translate

import android.media.Image
import kotlin.math.abs

/**
 * Decides when the camera has settled on something, so a frame can be taken without a shutter
 * button.
 *
 * The app cannot run OCR on every frame — a vision encode plus a translation is several seconds, so
 * "live" capture has to mean picking one good moment rather than reading continuously. The moment
 * worth picking is when the user has stopped moving: they have aimed at what they want.
 *
 * Each frame is reduced to a coarse grid of average luma and compared with the previous one. Hand
 * movement, or the scene changing, produces a large mean difference; a held phone produces only
 * sensor noise. [STABLE_FRAMES_REQUIRED] consecutive quiet comparisons is the trigger.
 *
 * Not thread-safe: it is driven from a single analyzer executor.
 */
class SceneStabilityDetector {

    private var previous: IntArray? = null
    private var stableFrames = 0

    /** Milliseconds since the detector was created or last reset. */
    private var startedAt = System.currentTimeMillis()

    /** How settled the scene currently looks, for the on-screen hint. */
    enum class Reading { MOVING, SETTLING, STABLE }

    fun reset() {
        previous = null
        stableFrames = 0
        startedAt = System.currentTimeMillis()
    }

    /**
     * Folds one frame in and reports what the scene looks like now.
     *
     * [Reading.STABLE] is returned once — subsequent frames keep returning it, so callers must
     * guard against capturing twice themselves.
     */
    fun offer(image: Image): Reading {
        val signature = signatureOf(image)
        val last = previous
        previous = signature

        // Auto-exposure and auto-focus are still converging for the first moments after the camera
        // opens, which shakes the luma enough to read as movement. Waiting it out avoids both a
        // spurious "moving" hint and a capture of a frame that has not focused yet.
        if (last == null || System.currentTimeMillis() - startedAt < SETTLE_GRACE_MS) {
            return Reading.MOVING
        }

        var total = 0L
        for (i in signature.indices) total += abs(signature[i] - last[i]).toLong()
        val meanDifference = total.toDouble() / signature.size

        if (meanDifference > MOVEMENT_THRESHOLD) {
            stableFrames = 0
            return Reading.MOVING
        }

        stableFrames++
        return if (stableFrames >= STABLE_FRAMES_REQUIRED) Reading.STABLE else Reading.SETTLING
    }

    /**
     * Average luma over a [GRID] x [GRID] grid.
     *
     * Only the Y plane is read. Colour adds nothing to a movement test and YUV keeps luma in its
     * own plane, so this needs no conversion — which matters when it runs on every frame.
     */
    private fun signatureOf(image: Image): IntArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        val signature = IntArray(GRID * GRID)
        for (gridY in 0 until GRID) {
            for (gridX in 0 until GRID) {
                var sum = 0
                var samples = 0
                // A few pixels per cell is plenty: this is a movement test, not an image.
                for (sampleY in 0 until SAMPLES_PER_CELL) {
                    val y = (gridY * height / GRID) + (sampleY * height / (GRID * SAMPLES_PER_CELL))
                    for (sampleX in 0 until SAMPLES_PER_CELL) {
                        val x = (gridX * width / GRID) + (sampleX * width / (GRID * SAMPLES_PER_CELL))
                        val index = y * rowStride + x * pixelStride
                        if (index in 0 until buffer.limit()) {
                            sum += buffer.get(index).toInt() and 0xFF
                            samples++
                        }
                    }
                }
                signature[gridY * GRID + gridX] = if (samples == 0) 0 else sum / samples
            }
        }
        return signature
    }

    private companion object {
        const val GRID = 16
        const val SAMPLES_PER_CELL = 3

        /**
         * Mean per-cell luma change, 0–255, above which the scene counts as moving. Low enough to
         * catch a slow pan, high enough to ignore sensor noise on a phone held still.
         */
        const val MOVEMENT_THRESHOLD = 4.0

        /** At a typical 15–30 analysis fps this is roughly a third of a second held still. */
        const val STABLE_FRAMES_REQUIRED = 6

        /** Long enough for auto-exposure and auto-focus to converge after the camera opens. */
        const val SETTLE_GRACE_MS = 900L
    }
}
