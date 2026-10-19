package org.freevia.sudokubuddy.vision

/** Interleaved RGB bytes; color is retained only for captured-image recognition. */
data class RgbImage(val width: Int, val height: Int, val pixels: ByteArray) {
    init {
        require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height * 3)
    }
}
