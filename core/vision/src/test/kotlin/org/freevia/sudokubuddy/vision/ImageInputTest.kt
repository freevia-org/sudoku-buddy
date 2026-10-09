package org.freevia.sudokubuddy.vision

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ImageInputTest {
    @Test
    fun `overflowing image dimensions cannot disguise an undersized buffer`() {
        assertFailsWith<IllegalArgumentException> { GrayImage(65_536, 65_536, ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { RgbImage(65_536, 65_536, ByteArray(0)) }
    }

    @Test
    fun `extremely thin imported images are rejected without a native resize failure`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        for ((width, height) in listOf(6_000 to 1, 1 to 6_000)) {
            val image = GrayImage(width, height, ByteArray(width * height) { 230.toByte() })
            assertIs<GateVerdict.Rejected>(StructuralGate.assess(image))
        }
    }

    @Test
    fun `invalid detection scales are rejected before native allocation`() {
        val image = GrayImage(1, 1, byteArrayOf(0))
        for (edge in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { QuadDetector.detect(image, edge) }
            assertFailsWith<IllegalArgumentException> { CellGrid.lattices(image, edge) }
        }
    }
}
