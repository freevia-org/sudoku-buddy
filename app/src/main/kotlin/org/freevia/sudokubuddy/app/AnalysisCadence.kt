package org.freevia.sudokubuddy.app

/** Bounds costly live detection to five checks a second; skipped proxies are still closed. */
internal class AnalysisCadence(private val intervalMillis: Long = 200L) {
    private var lastAnalysisMillis: Long? = null

    fun shouldAnalyze(nowMillis: Long): Boolean {
        val previous = lastAnalysisMillis
        if (previous != null && nowMillis >= previous && nowMillis - previous < intervalMillis) {
            return false
        }
        lastAnalysisMillis = nowMillis
        return true
    }
}
