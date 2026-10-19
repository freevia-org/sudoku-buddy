package org.freevia.sudokubuddy.vision

import kotlin.test.*

class PhotoLumaTest {
    @Test fun integerAndroidLevelsArePreservedWithoutRounding() {
        val rgb=byteArrayOf(255.toByte(),0,0,0,255.toByte(),0,0,0,255.toByte(),100,150.toByte(),200.toByte())
        assertEquals(listOf(76,149,29,140),PhotoLuma.image(4,1,rgb).pixels.map { it.toInt() and 255 })
        for (level in 0..255) assertEquals(level,PhotoLuma.pixel(level,level,level))
    }
    @Test fun malformedDimensionsDoNotReachInference() {
        assertFailsWith<IllegalArgumentException>{PhotoLuma.image(2,2,ByteArray(3))}
    }
}
