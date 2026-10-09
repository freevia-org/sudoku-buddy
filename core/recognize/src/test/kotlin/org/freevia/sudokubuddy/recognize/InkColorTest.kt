package org.freevia.sudokubuddy.recognize

import org.freevia.sudokubuddy.vision.RgbImage
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class InkColorTest {
    private fun sample(paper: IntArray, pen: IntArray): Pair<RgbImage, CellInk> {
        val pixels = ByteArray(30 * 30 * 3)
        for (y in 0 until 30) for (x in 0 until 30) {
            val rgb = if (x in 12..17 && y in 6..23) pen else paper
            for (channel in 0..2) pixels[(y * 30 + x) * 3 + channel] = rgb[channel].toByte()
        }
        return RgbImage(30, 30, pixels) to CellInk(
            Blob(12, 6, 6, 18, 108, 1.0 / 3, 0.6, 0.0, 30.0, 200.0, 6.0, 1),
            FloatArray(28 * 28), 0.0, 0,
        )
    }

    @Test
    fun `warm lighting and stock do not turn neutral ink into colored pen`() {
        val (image, ink) = sample(intArrayOf(250, 225, 200), intArrayOf(50, 45, 40))
        assertTrue(InkColor.chroma(image, ink) < 0.01)
    }

    @Test
    fun `blue pen carries strong chroma relative to its local paper`() {
        val (image, ink) = sample(intArrayOf(250, 225, 200), intArrayOf(30, 40, 160))
        assertTrue(InkColor.chroma(image, ink) > 0.5)
    }

    @Test
    fun `weak pen overlapping neutral noise cannot override grayscale roles`() {
        assertFalse(InkColor.separated(listOf(0.01, 0.10, 0.23, 0.24, 0.26, 0.29, 0.34), 0.25))
    }

    @Test
    fun `well separated neutral and colored populations provide role evidence`() {
        assertTrue(InkColor.separated(listOf(0.01, 0.04, 0.08, 0.38, 0.50, 0.60), 0.25))
    }
}
