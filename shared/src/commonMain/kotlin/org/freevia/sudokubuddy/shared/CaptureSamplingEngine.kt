package org.freevia.sudokubuddy.shared

import org.freevia.sudokubuddy.app.CaptureSampling

class CaptureSamplingEngine {
    // An image with no pixels is the decoder's failure to report, not a reason to end the process.
    fun sampleSize(width: Int, height: Int): Int =
        if (width > 0 && height > 0) CaptureSampling.sampleSize(width,height) else 1
}
