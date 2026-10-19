package org.freevia.sudokubuddy.vision

/** Android adapter: image location stays native; capture decisions are shared with iOS. */
class FramingAdvisor(stableFramesRequired: Int = 5, steadyTolerance: Double = 12.0) {
    private val policy = CapturePolicy(stableFramesRequired, steadyTolerance)
    fun reset() = policy.reset()
    fun advise(frame: GrayImage): Guidance {
        val located = GridLocator.locate(frame)
        if (located is GridLocation.Found) return adviseLocated(frame, located.quad, ImageQuality.of(located.rectified))
        val missed = located as? GridLocation.NoGrid
        return policy.missed(frame.width, frame.height, ImageQuality.of(frame).captureQuality(),
            missed?.candidatesConsidered ?: 0,
            (missed?.bestScore ?: 0.0) >= GridLocator.MIN_GRID_SCORE * 0.6,
            missed?.best?.takeIf { QuadDetector.couldBeAGrid(it) }?.corners?.map { Corner(it.x / frame.width, it.y / frame.height) })
    }
    internal fun adviseLocated(frame: GrayImage, quad: Quad, quality: ImageQuality): Guidance =
        policy.adviseLocated(frame.width, frame.height, quad, quality.captureQuality())
    internal fun shapeComplaint(quad: Quad): String? = policy.shapeComplaint(quad)
}

private fun ImageQuality.captureQuality() = CaptureQuality(sharpness, meanLuma, clippedWhiteFraction, worstQuadrantSharpnessRatio)
