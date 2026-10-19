package org.freevia.sudokubuddy.vision

import kotlin.test.Test
import kotlin.test.assertEquals

class CapturePhotometryTest {
    @Test fun `portable camera measurements match OpenCV including quadrant borders`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        for ((width,height) in listOf(32 to 32, 33 to 47, 160 to 120)) {
            val image = GrayImage(width,height,ByteArray(width*height){ ((it*31+it/width*17)%256).toByte() })
            val reference=ImageQuality.of(image)
            val portable=CapturePhotometry.of(image)
            assertEquals(reference.sharpness,portable.sharpness,1e-7)
            assertEquals(reference.meanLuma,portable.meanLuma,1e-9)
            assertEquals(reference.clippedWhiteFraction,portable.clippedWhiteFraction,1e-9)
            assertEquals(reference.worstQuadrantSharpnessRatio,portable.worstQuadrantSharpnessRatio,1e-9)
        }
    }
}
