package org.freevia.sudokubuddy.vision

/**
 * An 8-bit grayscale image, row-major, one byte per pixel.
 *
 * This is the only thing the vision module accepts. Callers convert to it from whatever
 * they have - `BufferedImage` on the JVM, `ImageProxy` or `Bitmap` on Android - which
 * keeps every platform image type, and OpenCV itself, out of this module's API.
 */
class GrayImage(val width: Int, val height: Int, val pixels: ByteArray) {

    init {
        require(width > 0 && height > 0) { "image dimensions must be positive" }
        val expectedSize = width.toLong() * height
        require(pixels.size.toLong() == expectedSize) {
            "a ${width}x$height image needs $expectedSize bytes but got ${pixels.size}"
        }
    }

    /** The pixel at ([x], [y]) as an unsigned value in `0..255`. */
    operator fun get(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xFF

    override fun toString(): String = "GrayImage(${width}x$height)"
}
