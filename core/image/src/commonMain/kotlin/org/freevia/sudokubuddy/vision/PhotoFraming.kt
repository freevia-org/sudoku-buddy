package org.freevia.sudokubuddy.vision

import kotlin.math.abs

/** Advisory framing for an already located photo. It never rejects a reading. */
object PhotoFraming {
    fun note(quad: Quad, width: Int, height: Int): String? {
        if (minOf(quad.topEdge, quad.rightEdge, quad.bottomEdge, quad.leftEdge) < 550.0)
            return "Move closer - the grid is too small to read."
        if (quad.corners.any { it.x <= 3.0 || it.y <= 3.0 || it.x >= width - 3.0 || it.y >= height - 3.0 })
            return "Fit the whole grid in view."
        if (quad.oppositeSideRatio > CaptureLimits.MAX_OPPOSITE_SIDE_RATIO || quad.maxCornerAngleDeviation > CaptureLimits.MAX_CORNER_ANGLE_DEVIATION)
            return "Hold the phone flat above the puzzle."
        if (abs(quad.rotationDegrees) > CaptureLimits.MAX_ROTATION_DEGREES)
            return "Turn the phone so the puzzle is upright."
        return null
    }
}
