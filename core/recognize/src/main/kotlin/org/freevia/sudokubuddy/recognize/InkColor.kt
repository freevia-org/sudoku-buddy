package org.freevia.sudokubuddy.recognize

import org.freevia.sudokubuddy.vision.RgbImage

/** Paper-relative chroma, so warm lighting and colored stock do not look like pen ink. */
internal object InkColor {
    /** Abstain when weakly colored pen overlaps the page's own neutral-color noise. */
    fun separated(chroma: List<Double>, cutoff: Double): Boolean {
        val neutral = chroma.filter { it <= cutoff }
        val colored = chroma.filter { it > cutoff }
        if (neutral.isEmpty() || colored.size < 3) return false
        val noise = neutral.max() - neutral.min()
        val gap = colored.min() - neutral.max()
        return gap > noise
    }

    fun chroma(cell: RgbImage, ink: CellInk): Double {
        val channels = Array(3) { IntArray(cell.width * cell.height) }
        for (pixel in channels[0].indices) for (channel in 0..2) {
            channels[channel][pixel] = cell.pixels[pixel * 3 + channel].toInt() and 255
        }
        val paper = channels.map { it.sorted()[it.size / 2].coerceAtLeast(1).toDouble() }
        val blob = ink.blob
        val weighted = DoubleArray(3)
        var weight = 0.0
        for (y in blob.top.coerceAtLeast(0) until (blob.top + blob.height).coerceAtMost(cell.height)) {
            for (x in blob.left.coerceAtLeast(0) until (blob.left + blob.width).coerceAtMost(cell.width)) {
                val index = y * cell.width + x
                val density = DoubleArray(3) { channel ->
                    (1.0 - channels[channel][index] / paper[channel]).coerceAtLeast(0.0)
                }
                val dark = density.average()
                // Weight dark pixels quadratically; background and JPEG fringe carry little evidence.
                val w = dark * dark
                for (channel in 0..2) weighted[channel] += density[channel] * w
                weight += w
            }
        }
        if (weight == 0.0) return 0.0
        val mean = weighted.map { it / weight }
        return (mean.max() - mean.min()) / mean.max().coerceAtLeast(0.01)
    }
}
