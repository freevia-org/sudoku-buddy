package org.freevia.sudokubuddy.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CaptureSamplingTest {
    @Test
    fun `ordinary twelve megapixel captures keep all their pixels`() {
        assertEquals(1, CaptureSampling.sampleSize(4_000, 3_000))
        assertEquals(1, CaptureSampling.sampleSize(4_032, 3_024))
        assertEquals(1, CaptureSampling.sampleSize(3_024, 4_032))
        assertEquals(1, CaptureSampling.sampleSize(4_096, 3_072))
    }

    @Test
    fun `high resolution captures are sampled before allocating their full bitmap`() {
        assertEquals(2, CaptureSampling.sampleSize(8_000, 6_000))
        assertEquals(2, CaptureSampling.sampleSize(8_064, 6_048))
        assertEquals(4, CaptureSampling.sampleSize(16_000, 12_000))
    }

    @Test
    fun `odd and extreme dimensions stay within the budget without integer overflow`() {
        for ((width, height) in listOf(4_097 to 3_072, 8_193 to 6_145,
            Int.MAX_VALUE to Int.MAX_VALUE, Int.MAX_VALUE to 1, 1 to Int.MAX_VALUE)) {
            val sample = CaptureSampling.sampleSize(width, height)
            fun pixels(at: Int) = ((width.toLong() + at - 1) / at) *
                ((height.toLong() + at - 1) / at)
            assertEquals(0, sample and (sample - 1), "sampling must use a power of two")
            assertTrue(pixels(sample) <= CaptureSampling.MAX_PIXELS)
            if (sample > 1) assertTrue(pixels(sample / 2) > CaptureSampling.MAX_PIXELS,
                "retain the highest resolution that fits")
        }
    }

    @Test
    fun `invalid dimensions are rejected before sampling`() {
        assertFailsWith<IllegalArgumentException> { CaptureSampling.sampleSize(0, 100) }
        assertFailsWith<IllegalArgumentException> { CaptureSampling.sampleSize(100, -1) }
    }
}
