package org.freevia.sudokubuddy.vision

import kotlin.test.*

class CapturePolicyTest {
    private val quad = Quad(Corner(120.0,180.0), Corner(600.0,180.0), Corner(600.0,660.0), Corner(120.0,660.0))
    private val good = CaptureQuality(100.0,200.0,0.0,0.7)
    private fun CapturePolicy.good(q: Quad = quad, quality: CaptureQuality = good) = adviseLocated(720,960,q,quality)
    private fun CapturePolicy.miss() = missed(720,960,good,0,false)
    @Test fun fiveStableFramesAfterFirstSighting() {
        val policy = CapturePolicy()
        repeat(5) { assertFalse(policy.good().readyToCapture) }
        assertTrue(policy.good().readyToCapture)
        policy.reset(); assertFalse(policy.good().readyToCapture)
    }
    @Test fun twoMissesAreForgivenButThirdResets() {
        for (misses in 1..3) {
            val policy = CapturePolicy(); repeat(5) { policy.good() }
            repeat(misses) { assertFalse(policy.miss().readyToCapture) }
            assertEquals(misses <= 2, policy.good().readyToCapture)
        }
    }
    @Test fun qualityComplaintsResetRunAndUseGridRegion() {
        val policy = CapturePolicy(); repeat(6) { policy.good() }
        assertEquals("More light needed", policy.good(quality=good.copy(meanLuma=59.9)).message)
        assertFalse(policy.good().readyToCapture)
        assertEquals("Avoid the glare",policy.good(quality=good.copy(clippedWhiteFraction=0.041)).message)
        assertEquals("Too dark to make out the grid lines",policy.missed(720,960,good.copy(meanLuma=20.0),0,false).message)
        assertEquals("Glare is washing the lines out - tilt the page away from the light",policy.missed(720,960,good.copy(clippedWhiteFraction=0.09),0,false).message)
    }
    @Test fun driftToleranceScalesWithFrame() {
        for (scale in listOf(0.5,1.0,2.0)) {
            val policy=CapturePolicy(stableFramesRequired=1)
            fun q(dx: Double) = Quad.ordering(quad.corners.map { Corner((it.x+dx)*scale,it.y*scale) })
            policy.adviseLocated((720*scale).toInt(),(960*scale).toInt(),q(0.0),good)
            assertTrue(policy.adviseLocated((720*scale).toInt(),(960*scale).toInt(),q(10.0),good).readyToCapture)
            assertFalse(policy.adviseLocated((720*scale).toInt(),(960*scale).toInt(),q(25.0),good).readyToCapture)
        }
    }
    @Test fun relativeWholeAndQuadrantFocusMustRecover() {
        val policy=CapturePolicy(stableFramesRequired=2)
        repeat(3){policy.good()}
        assertFalse(policy.good(quality=good.copy(sharpness=40.0)).readyToCapture)
        assertFalse(policy.good(quality=good.copy(worstQuadrantSharpnessRatio=0.3)).readyToCapture)
        assertTrue(policy.good().readyToCapture)
    }
    @Test fun shapeEdgeAndSizeRules() {
        val policy=CapturePolicy()
        assertEquals("Move closer",policy.good(Quad(Corner(200.0,200.0),Corner(300.0,200.0),Corner(300.0,300.0),Corner(200.0,300.0))).message)
        assertEquals("Fit the whole grid in view",policy.good(Quad(Corner(0.0,180.0),Corner(480.0,180.0),Corner(480.0,660.0),Corner(0.0,660.0))).message)
        assertEquals("Hold the phone flat",policy.shapeComplaint(Quad(Corner(100.0,100.0),Corner(600.0,300.0),Corner(600.0,700.0),Corner(100.0,700.0))))
    }
    @Test fun photometryFlatAndKnownImpulse() {
        val flat=CapturePhotometry.of(GrayImage(20,20,ByteArray(400){200.toByte()}))
        assertEquals(CaptureQuality(0.0,200.0,0.0,1.0),flat)
        val pixels=ByteArray(400);pixels[10*20+10]=100
        val impulse=CapturePhotometry.of(GrayImage(20,20,pixels))
        assertEquals(500.0,impulse.sharpness,1e-9)
        assertEquals(0.25,impulse.meanLuma,1e-9)
    }
}
