package org.freevia.sudokubuddy.vision

/** Android's integer Rec.601 conversion, before any geometric interpolation. */
object PhotoLuma {
    fun pixel(red: Int, green: Int, blue: Int): Int = (red * 299 + green * 587 + blue * 114) / 1000
    fun image(width: Int, height: Int, rgb: ByteArray): GrayImage {
        require(rgb.size.toLong() == width.toLong() * height * 3)
        return GrayImage(width,height,ByteArray(width*height) { index ->
            val at=index*3
            pixel(rgb[at].toInt() and 255,rgb[at+1].toInt() and 255,rgb[at+2].toInt() and 255).toByte()
        })
    }
}
