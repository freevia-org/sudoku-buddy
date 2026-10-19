package org.freevia.sudokubuddy.vision
import kotlin.math.roundToInt
internal fun gridInkProfiles(image: GrayImage): Pair<DoubleArray, DoubleArray> = portableGridInkProfiles(image)

/** Adaptive mean threshold with replicated borders, matching the JVM line fitter. */
internal fun portableGridInkProfiles(image: GrayImage): Pair<DoubleArray, DoubleArray> {
    val w = image.width; val h = image.height
    val stride = w + 31
    val sum = LongArray(stride * (h + 31))
    for (y in 0 until h + 30) {
        var row = 0L
        for (x in 0 until w + 30) {
            row += image[(x - 15).coerceIn(0, w - 1), (y - 15).coerceIn(0, h - 1)]
            sum[(y + 1) * stride + x + 1] = sum[y * stride + x + 1] + row
        }
    }
    val columns = DoubleArray(w); val rows = DoubleArray(h)
    for (y in 0 until h) for (x in 0 until w) {
        val total = sum[(y + 31) * stride + x + 31] - sum[y * stride + x + 31] -
            sum[(y + 31) * stride + x] + sum[y * stride + x]
        val mean = (total / 961.0).roundToInt()
        if (image[x, y] <= mean - 10) { columns[x]++; rows[y]++ }
    }
    return columns to rows
}
