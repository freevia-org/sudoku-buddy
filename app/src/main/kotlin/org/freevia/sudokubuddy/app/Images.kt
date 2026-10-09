package org.freevia.sudokubuddy.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ImageProxy
import org.freevia.sudokubuddy.vision.GrayImage
import org.freevia.sudokubuddy.vision.RgbImage

/**
 * Conversions from what Android hands us into the one type the vision module accepts.
 *
 * The preview path is free: a YUV_420_888 frame's first plane *is* the luma image, so
 * grayscale conversion is a copy rather than a computation. That matters when it runs
 * on every frame.
 */
object Images {

    /** The luma plane of a camera frame, honouring its row stride. */
    fun fromPreview(proxy: ImageProxy): GrayImage {
        val plane = proxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val width = proxy.width
        val height = proxy.height

        val pixels = ByteArray(width * height)
        val row = ByteArray(rowStride)
        for (y in 0 until height) {
            buffer.position(y * rowStride)
            val available = minOf(rowStride, buffer.remaining())
            buffer.get(row, 0, available)
            System.arraycopy(row, 0, pixels, y * width, minOf(width, available))
        }
        return GrayImage(width, height, pixels)
    }

    data class Photograph(val gray: GrayImage, val rgb: RgbImage)

    /** Inspect dimensions before allocating pixels, then retain grayscale and color. */
    fun photograph(bytes: ByteArray, rotationDegrees: Int): Photograph {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "could not decode the captured image" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = CaptureSampling.sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("could not decode the captured image")
        var upright = decoded
        return try {
            if (rotationDegrees != 0) {
                upright = rotate(decoded, rotationDegrees)
                if (upright !== decoded) decoded.recycle()
            }
            val width = upright.width
            val height = upright.height
            // A row buffer avoids another full ARGB photograph beside the bitmap and RGB.
            val row = IntArray(width)
            val gray = ByteArray(width * height)
            val rgb = ByteArray(width * height * 3)
            for (y in 0 until height) {
                upright.getPixels(row, 0, width, 0, y, width, 1)
                for (x in 0 until width) {
                    val i = y * width + x
                    val p = row[x]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    gray[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
                    rgb[i * 3] = r.toByte()
                    rgb[i * 3 + 1] = g.toByte()
                    rgb[i * 3 + 2] = b.toByte()
                }
            }
            Photograph(GrayImage(width, height, gray), RgbImage(width, height, rgb))
        } finally {
            upright.recycle()
        }
    }

    fun fromJpeg(bytes: ByteArray, rotationDegrees: Int): GrayImage =
        photograph(bytes, rotationDegrees).gray

    fun fromBitmap(bitmap: Bitmap): GrayImage {
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val pixels = ByteArray(width * height)
        for (i in argb.indices) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Rec. 601 luma, matching what the JVM side uses so measurements transfer.
            pixels[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
        }
        return GrayImage(width, height, pixels)
    }

    /** A grayscale image back to a bitmap, for display. */
    fun toBitmap(image: GrayImage): Bitmap {
        val argb = IntArray(image.width * image.height)
        for (i in argb.indices) {
            val v = image.pixels[i].toInt() and 0xFF
            argb[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(argb, image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
