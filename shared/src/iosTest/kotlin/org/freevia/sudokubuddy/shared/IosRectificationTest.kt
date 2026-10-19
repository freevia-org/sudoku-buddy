@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class,kotlinx.cinterop.BetaInteropApi::class)
package org.freevia.sudokubuddy.shared

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.test.*

class IosRectificationTest {
    @Test fun nativeDataBridgeUsesSharedWarpAndPreWarpIntegerLuma() {
        val pixels=ByteArray(32*32*3){ when(it%3){0->100;1->150;else->200}.toByte() }
        val input=pixels.usePinned { NSData.create(bytes=it.addressOf(0),length=pixels.size.toULong()) }
        val result=IosScanner().rectify(32,32,input,"0,0,31,0,31,31,0,31")
        assertEquals(1152L*1152,result.luminance.length.toLong())
        assertEquals(1152L*1152*3,result.rgb.length.toLong())
        val gray=ByteArray(result.luminance.length.toInt()).also { bytes -> bytes.usePinned { memcpy(it.addressOf(0),result.luminance.bytes,result.luminance.length) } }
        assertTrue(gray.all { (it.toInt() and 255)==140 })
    }
}
