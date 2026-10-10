package org.freevia.sudokubuddy.app

/** Keeps training permission scoped to one reading and immutable after its first accepted send. */
internal object ReportTrainingConsent {
    fun forAutomaticSubmission(
        hasAcceptedSubmission: Boolean,
        existingConsent: Boolean,
        scanOptIn: Boolean,
        trainingEnabledNow: Boolean,
    ): Boolean = if (hasAcceptedSubmission) existingConsent else scanOptIn && trainingEnabledNow

    fun forManualSubmission(
        hasAcceptedSubmission: Boolean,
        existingConsent: Boolean,
        userConsented: Boolean,
    ): Boolean = if (hasAcceptedSubmission) existingConsent else userConsented
}
