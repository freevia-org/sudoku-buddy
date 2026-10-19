@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package org.freevia.sudokubuddy.shared

import kotlinx.cinterop.*
import platform.Foundation.*
import kotlin.test.*

class IosCaptureAdvisorTest {
    private fun pixels(w: Int, h: Int, value: Int): NSData = ByteArray(w*h){value.toByte()}.usePinned {
        NSData.create(bytes=it.addressOf(0),length=(w*h).toULong())
    }
    @Test fun nativeBridgeUsesStableFramesAndReset() {
        val advisor=IosCaptureAdvisor()
        val frame=pixels(720,960,200)
        val grid=pixels(32,32,200)
        fun observe()=advisor.observe(720,960,frame,"0.1666667,0.1875,0.8333333,0.1875,0.8333333,0.6875,0.1666667,0.6875",32,grid,1,true)
        repeat(5){assertTrue(observe().contains("\"ready\":false"))}
        assertTrue(observe().contains("\"ready\":true"))
        advisor.reset();assertTrue(observe().contains("\"ready\":false"))
        assertTrue(advisor.observe(720,960,pixels(720,960,20),"",0,NSData(),0,false).contains("Too dark"))
    }
}
