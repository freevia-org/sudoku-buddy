package org.freevia.sudokubuddy.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportTrainingConsentTest {
    @Test
    fun `auto sharing and training permissions start disabled`() {
        assertFalse(Settings().autoShareWhenUncertain)
        assertFalse(Settings().trainAutoSharedReports)
    }

    @Test
    fun `auto training requires scan-time and current opt in`() {
        assertTrue(ReportTrainingConsent.forAutomaticSubmission(false, false, true, true))
        assertFalse(ReportTrainingConsent.forAutomaticSubmission(false, false, true, false))
        assertFalse(ReportTrainingConsent.forAutomaticSubmission(false, false, false, true))
    }

    @Test
    fun `manual choice applies only before first accepted report`() {
        assertTrue(ReportTrainingConsent.forManualSubmission(false, false, true))
        assertFalse(ReportTrainingConsent.forManualSubmission(false, false, false))
        assertFalse(ReportTrainingConsent.forManualSubmission(true, false, true))
        assertTrue(ReportTrainingConsent.forManualSubmission(true, true, false))
    }
}
