package org.freevia.sudokubuddy.shared

import org.freevia.sudokubuddy.app.CaptureSampling

class CaptureSamplingEngine {
    fun sampleSize(width: Int, height: Int): Int = CaptureSampling.sampleSize(width,height)
}
