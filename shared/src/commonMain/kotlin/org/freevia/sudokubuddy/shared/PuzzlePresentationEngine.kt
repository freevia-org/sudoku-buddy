package org.freevia.sudokubuddy.shared

import org.freevia.sudokubuddy.app.*
import org.freevia.sudokubuddy.recognize.Ink
import org.freevia.sudokubuddy.solver.SolveResult

/** The same layer toggles, answer cycling, diagnostics and legends used by Android. */
data class ReadingReviewPresentation(val available:Boolean,val visible:Boolean)

data class PuzzleWindowLayout(val sideBySide: Boolean,val photoSide: Double)

class PuzzlePresentationEngine {
    fun readingReview(openQuestions:Int,originalQuestions:Int,expanded:Boolean,showing:String):ReadingReviewPresentation {
        val available=PuzzleLogic.hasReadingReview(openQuestions,originalQuestions)
        return ReadingReviewPresentation(available,PuzzleLogic.showReadingReview(available,expanded,false,mode(showing)))
    }
    /** SwiftUI already removes its system safe area; Android's window includes it. */
    fun layout(width:Double,height:Double,fontScale:Double,chrome:Double):PuzzleWindowLayout {
        val value=PuzzleLayout.forWindow(width.toFloat(),height.toFloat(),fontScale.toFloat(),chrome.toFloat())
        return PuzzleWindowLayout(value.sideBySide,value.photoSide.toDouble())
    }
    // An exception leaving Kotlin ends the iOS process, and the caller already ignores
    // a snapshot it cannot decode.
    fun snapshot(givens: String, progress: String, showing: String, pressed: String, answerShown: Int, uncertain: Boolean, enteredCells: String = ""): String = runCatching {
        val grid = gridOf(givens, progress)
        val current = mode(showing)
        val button = mode(pressed)
        val count = if (button == OverlayMode.SOLUTION) PuzzleLogic.offeredAnswers(grid).size else 0
        val stepped = if (pressed.isNotEmpty()) PuzzleLogic.steppedAnswer(current, button, answerShown, count) else null
        val next = if (pressed.isEmpty()) current else if (stepped != null) button else PuzzleLogic.press(current, button, 0, HintStyle.EXPLAIN).mode
        val answer = stepped ?: if (next == OverlayMode.SOLUTION && current == next) answerShown else 0
        val overlay = PuzzleLogic.overlay(grid, next, HintStyle.EXPLAIN, entered = cellSet(enteredCells), answerShown = answer)
        val guidance = PuzzleLogic.guidance(grid, next, HintStyle.EXPLAIN, answerShown = answer)?.body.orEmpty()
        obj("mode" to jsonString(name(next)), "answerShown" to answer.toString(), "answerCount" to count.toString(),
            "guidance" to jsonString(guidance), "status" to jsonString(PuzzleLogic.status(grid)?.text.orEmpty()),
            "canHint" to PuzzleLogic.canHint(grid, HintStyle.REVEAL).toString(), "unique" to (PuzzleLogic.solveResult(grid) is SolveResult.Unique).toString(),
            "digits" to array((0 until 81).map { overlay.digits[it]?.digit?.toString() ?: "0" }),
            "roles" to array((0 until 81).map { jsonString(overlay.digits[it]?.role?.name.orEmpty()) }),
            "evidence" to ints(overlay.evidence), "focus" to (overlay.focus?.toString() ?: "null"),
            "legend" to array(PuzzleLogic.legend(overlay, next, uncertain).map { jsonString(it.name) }))
    }.getOrElse { obj() }
    fun historySummary(givens: String, progress: String): String =
        runCatching { PuzzleLogic.historySummary(gridOf(givens, progress)) }.getOrDefault("")
    fun readingHeadline(count: Int): String = PuzzleLogic.readingHeadline(count)
    fun describeReading(ink: String, digit: Int, confidence: Double, runnerUp: Int, runnerUpConfidence: Double, roleUncertain: Boolean): String {
        // Saved evidence may name a kind of ink this build does not know.
        val read = Ink.entries.firstOrNull { it.name == ink } ?: return ""
        return CellReport(read, digit.takeIf { it in 1..9 }, confidence.toFloat(),
            runnerUp.takeIf { it in 1..9 }, runnerUpConfidence.toFloat(), roleUncertain).describe()
    }
    fun secondGuess(digit: Int, runnerUp: Int, confidence: Double): String =
        CellReport(Ink.NONE, digit.takeIf { it in 1..9 }, 1f,
            runnerUp.takeIf { it in 1..9 }, confidence.toFloat()).secondGuess().orEmpty()
    private fun mode(name: String): OverlayMode = when (name) {
        "Read" -> OverlayMode.READING; "Check" -> OverlayMode.CHECK; "Solve" -> OverlayMode.SOLUTION; "Hint" -> OverlayMode.HINT
        else -> OverlayMode.NONE
    }
    private fun name(mode: OverlayMode) = when (mode) {
        OverlayMode.READING -> "Read"; OverlayMode.CHECK -> "Check"; OverlayMode.SOLUTION -> "Solve"
        else -> "None"
    }
}
