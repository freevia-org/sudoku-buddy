package org.freevia.sudokubuddy.vision

/** OpenCV's default 3x3 Laplacian (ksize=1, BORDER_REFLECT_101), without a native dependency. */
object CapturePhotometry {
    fun of(image: GrayImage): CaptureQuality {
        val width = image.width
        val height = image.height
        fun reflect(value: Int, limit: Int): Int = when {
            limit == 1 -> 0
            value < 0 -> 1
            value >= limit -> limit - 2
            else -> value
        }
        fun laplacian(x: Int, y: Int): Double = (
            image[reflect(x - 1, width), y] + image[reflect(x + 1, width), y] +
                image[x, reflect(y - 1, height)] + image[x, reflect(y + 1, height)] - 4 * image[x, y]
            ).toDouble()
        fun variance(left: Int, top: Int, columns: Int, rows: Int): Double {
            var sum = 0.0
            var squared = 0.0
            for (y in top until top + rows) for (x in left until left + columns) {
                val value = laplacian(x, y)
                sum += value
                squared += value * value
            }
            val count = columns.toDouble() * rows
            return (squared / count - (sum / count) * (sum / count)).coerceAtLeast(0.0)
        }
        var luma = 0.0
        var clipped = 0
        for (pixel in image.pixels) {
            val value = pixel.toInt() and 255
            luma += value
            if (value >= 250) clipped++
        }
        val halfWidth = width / 2
        val halfHeight = height / 2
        val ratio = if (halfWidth < 8 || halfHeight < 8) 1.0 else {
            val quarters = listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1).map { (x, y) ->
                variance(x * halfWidth, y * halfHeight, halfWidth, halfHeight)
            }.sorted()
            val median = (quarters[1] + quarters[2]) / 2
            if (median <= 0) 1.0 else quarters.first() / median
        }
        return CaptureQuality(variance(0, 0, width, height), luma / image.pixels.size,
            clipped.toDouble() / image.pixels.size, ratio)
    }
}
