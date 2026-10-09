package org.freevia.sudokubuddy.recognize

import org.freevia.sudokubuddy.vision.GrayImage
import org.freevia.sudokubuddy.vision.OpenCvNatives
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CellAnalyzerBufferTest {
    private fun cell(dx: Int = 0, dy: Int = 0): GrayImage {
        val width = 73
        val height = 91
        val pixels = ByteArray(width * height) { 230.toByte() }
        // Unequal dimensions and an off-centre glyph expose incorrect label strides.
        for (y in 19 + dy until 52 + dy) for (x in 22 + dx until 29 + dx) {
            pixels[y * width + x] = 60
        }
        return GrayImage(width, height, pixels)
    }

    @Test
    fun `moving a glyph in a rectangular cell preserves its measurement and normalisation`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val readings = CellAnalyzer.inspect(listOf(cell(), cell(7, 4)))
        val first = assertNotNull(readings[0])
        val moved = assertNotNull(readings[1])
        assertEquals(first.blob.left + 7, moved.blob.left)
        assertEquals(first.blob.top + 4, moved.blob.top)
        assertEquals(first.blob.width, moved.blob.width)
        assertEquals(first.blob.height, moved.blob.height)
        assertEquals(first.blob.area, moved.blob.area)
        assertEquals(first.blob.darkness, moved.blob.darkness)
        assertEquals(first.blob.contrast, moved.blob.contrast)
        assertContentEquals(first.normalised, moved.normalised)
    }

    @Test
    fun `cells retain independent label buffers when interspersed with blank cells`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val blank = GrayImage(73, 91, ByteArray(73 * 91) { 230.toByte() })
        val input = (0 until 81).map { if (it % 3 == 0) cell() else blank }
        val readings = CellAnalyzer.inspect(input)
        val expected = assertNotNull(readings.first())
        for (index in readings.indices) {
            if (index % 3 == 0) {
                val reading = assertNotNull(readings[index])
                assertEquals(expected.blob, reading.blob)
                assertContentEquals(expected.normalised, reading.normalised)
            } else {
                assertNull(readings[index])
            }
        }
    }
}
