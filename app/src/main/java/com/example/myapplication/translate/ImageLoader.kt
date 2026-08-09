package com.example.myapplication.translate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Turns a picked or captured image into JPEG bytes suitable for the vision encoder.
 *
 * Two things matter here. Downscaling keeps a 12 MP camera photo from being handed to a model that
 * will resize it internally anyway — pointless memory pressure on a device already holding ~2.6 GB
 * of weights. And EXIF rotation must be applied, because phone cameras record orientation in
 * metadata rather than rotating pixels, and text read sideways does not get read at all.
 */
object ImageLoader {

    private const val TAG = "ImageLoader"
    private const val MAX_DIMENSION = 1536
    private const val JPEG_QUALITY = 88

    fun readAsJpeg(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            // The elvis must guard openInputStream, not the decode. With inJustDecodeBounds set,
            // decodeStream returns null by design and only fills in `bounds` — so binding the
            // null check to its result aborts every time, however valid the image.
            val stream = resolver.openInputStream(uri) ?: return null
            stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not read image bounds", t)
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.e(TAG, "Image has no usable dimensions")
            return null
        }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_DIMENSION) sample *= 2

        val decoded = try {
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                })
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not decode image", t)
            null
        } ?: return null

        val upright = applyExifRotation(context, uri, decoded)

        return try {
            ByteArrayOutputStream().use { out ->
                upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not encode image", t)
            null
        } finally {
            if (upright !== decoded) decoded.recycle()
            upright.recycle()
        }
    }

    /**
     * Normalises a still straight off the camera: downscaled, and rotated upright in pixels.
     *
     * The rotation cannot be left to EXIF here for the same reason it cannot for a gallery photo —
     * CameraX records orientation in metadata rather than turning the pixels, and the vision
     * encoder only ever sees pixels. Text captured sideways is text the model does not read.
     */
    fun decodeCapturedJpeg(jpeg: ByteArray, rotationDegrees: Int, crop: RectF? = null): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.e(TAG, "Captured frame has no usable dimensions")
            return null
        }

        // Sized against what survives the crop, not the whole frame. Sampling for the full image
        // first would throw away most of the detail in a small selection before it was ever read —
        // a band across the middle of a sign is exactly where the text needs to stay legible.
        //
        // The crop's axes are the upright ones, which a quarter turn swaps relative to the sensor,
        // so the dimensions have to be matched up before they are multiplied.
        val quarterTurned = rotationDegrees % 180 != 0
        val uprightWidth = if (quarterTurned) bounds.outHeight else bounds.outWidth
        val uprightHeight = if (quarterTurned) bounds.outWidth else bounds.outHeight

        val keptWidth = uprightWidth * (crop?.width() ?: 1f)
        val keptHeight = uprightHeight * (crop?.height() ?: 1f)
        var sample = 1
        while (max(keptWidth, keptHeight) / sample > MAX_DIMENSION) sample *= 2

        val decoded = try {
            BitmapFactory.decodeByteArray(
                jpeg, 0, jpeg.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Could not decode captured frame", t)
            null
        } ?: return null

        // Rotate before cropping: the rectangle was drawn on an upright preview, so its
        // coordinates only mean anything once the pixels are upright too.
        val upright = rotate(decoded, rotationDegrees.toFloat())
        val selected = if (crop == null) upright else cropTo(upright, crop)

        return try {
            ByteArrayOutputStream().use { out ->
                selected.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not encode captured frame", t)
            null
        } finally {
            if (selected !== upright) selected.recycle()
            if (upright !== decoded) upright.recycle()
            decoded.recycle()
        }
    }

    /** Cuts out the normalised [crop], clamped so a degenerate rectangle cannot produce no pixels. */
    private fun cropTo(bitmap: Bitmap, crop: RectF): Bitmap {
        val left = (crop.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (crop.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (crop.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (crop.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)

        return try {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } catch (t: Throwable) {
            Log.w(TAG, "Crop failed; using the whole frame", t)
            bitmap
        }
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (t: Throwable) {
            Log.w(TAG, "No usable EXIF orientation", t)
            0f
        }

        return rotate(bitmap, degrees)
    }

    /** Returns [bitmap] itself when there is nothing to do, so callers can compare by identity. */
    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        return try {
            Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(degrees) }, true,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Rotation failed; using the image as-is", t)
            bitmap
        }
    }
}
