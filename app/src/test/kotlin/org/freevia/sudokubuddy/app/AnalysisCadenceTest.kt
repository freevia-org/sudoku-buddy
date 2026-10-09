package org.freevia.sudokubuddy.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalysisCadenceTest {
    @Test
    fun `live analysis drops surplus frames and resumes after a pause`() {
        val cadence = AnalysisCadence()
        assertTrue(cadence.shouldAnalyze(1000))
        for (time in 1001L until 1200L) assertFalse(cadence.shouldAnalyze(time))
        assertTrue(cadence.shouldAnalyze(1200))
        assertTrue(cadence.shouldAnalyze(9000))
        assertFalse(cadence.shouldAnalyze(9010))
    }
}
