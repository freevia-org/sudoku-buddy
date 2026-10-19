package org.freevia.sudokubuddy.vision

import kotlin.test.*

class PhotoFramingTest {
    private fun square(x: Double, y: Double, side: Double) = Quad(Corner(x,y),Corner(x+side,y),Corner(x+side,y+side),Corner(x,y+side))
    @Test fun smallAndClippedPhotosRemainAdvisoryWithAndroidPriority() {
        assertEquals("Move closer - the grid is too small to read.", PhotoFraming.note(square(0.0,0.0,549.0),1000,1000))
        assertEquals("Fit the whole grid in view.", PhotoFraming.note(square(3.0,4.0,550.0),1000,1000))
        assertNull(PhotoFraming.note(square(4.0,4.0,550.0),1000,1000))
        assertEquals("Fit the whole grid in view.", PhotoFraming.note(square(447.0,4.0,550.0),1000,1000))
    }
    @Test fun perspectiveComplaintMatchesCaptureShapeLimits() {
        val quad=Quad(Corner(100.0,100.0),Corner(1100.0,100.0),Corner(950.0,1000.0),Corner(250.0,1000.0))
        assertEquals("Hold the phone flat above the puzzle.", PhotoFraming.note(quad,1300,1300))
    }
}
