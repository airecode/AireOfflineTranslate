package com.example.myapplication.translate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
