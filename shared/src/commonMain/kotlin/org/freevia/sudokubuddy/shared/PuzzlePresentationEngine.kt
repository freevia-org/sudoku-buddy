package org.freevia.sudokubuddy.shared

import org.freevia.sudokubuddy.app.*
import org.freevia.sudokubuddy.model.Cell
import org.freevia.sudokubuddy.model.Grid
import org.freevia.sudokubuddy.solver.Solver

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
    fun snapshot(givens: String, progress: String, showing: String, pressed: String, answerShown: Int, uncertain: Boolean, enteredCells: String = ""): String {
        require(givens.length == 81 && progress.length == 81)
        var grid = Grid.fromGivens(givens)
        for (index in 0 until 81) {
            val digit = progress[index].digitToIntOrNull()?.takeIf { it != 0 } ?: continue
            if (!grid[index].isFilled) grid = grid.with(index, Cell.guess(digit))
        }
        val current = mode(showing)
        val button = mode(pressed)
        val count = if (button == OverlayMode.SOLUTION) Solver.solutions(grid, PuzzleLogic.MOST_ANSWERS_OFFERED).size else 0
        val stepped = if (pressed.isNotEmpty()) PuzzleLogic.steppedAnswer(current, button, answerShown, count) else null
        val next = if (pressed.isEmpty()) current else if (stepped != null) button else PuzzleLogic.press(current, button, 0, HintStyle.EXPLAIN).mode
        val answer = stepped ?: if (next == OverlayMode.SOLUTION && current == next) answerShown else 0
        val overlay = PuzzleLogic.overlay(grid, next, HintStyle.EXPLAIN,
            entered = enteredCells.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0..80 }.toSet(), answerShown = answer)
        val guidance = PuzzleLogic.guidance(grid, next, HintStyle.EXPLAIN, answerShown = answer)?.body.orEmpty()
        return obj("mode" to jsonString(name(next)), "answerShown" to answer.toString(), "answerCount" to count.toString(),
            "guidance" to jsonString(guidance), "status" to jsonString(PuzzleLogic.status(grid)?.text.orEmpty()),
            "canHint" to PuzzleLogic.canHint(grid, HintStyle.REVEAL).toString(), "unique" to Solver.hasUniqueSolution(grid).toString(),
            "digits" to array((0 until 81).map { overlay.digits[it]?.digit?.toString() ?: "0" }),
            "roles" to array((0 until 81).map { jsonString(overlay.digits[it]?.role?.name.orEmpty()) }),
            "evidence" to ints(overlay.evidence), "focus" to (overlay.focus?.toString() ?: "null"),
            "legend" to array(PuzzleLogic.legend(overlay, next, uncertain).map { jsonString(it.name) }))
    }
    fun historySummary(givens: String, progress: String): String {
        require(givens.length == 81 && progress.length == 81)
        var grid = Grid.fromGivens(givens)
        for (index in 0 until 81) {
            val digit = progress[index].digitToIntOrNull()?.takeIf { it != 0 } ?: continue
            if (!grid[index].isFilled) grid = grid.with(index, Cell.guess(digit))
        }
        return PuzzleLogic.historySummary(grid)
    }
    fun readingHeadline(count: Int): String = PuzzleLogic.readingHeadline(count)
    fun describeReading(ink: String, digit: Int, confidence: Double, runnerUp: Int, runnerUpConfidence: Double, roleUncertain: Boolean): String =
        CellReport(org.freevia.sudokubuddy.recognize.Ink.valueOf(ink), digit.takeIf { it in 1..9 }, confidence.toFloat(),
            runnerUp.takeIf { it in 1..9 }, runnerUpConfidence.toFloat(), roleUncertain).describe()
    fun secondGuess(digit: Int, runnerUp: Int, confidence: Double): String =
        CellReport(org.freevia.sudokubuddy.recognize.Ink.NONE, digit.takeIf { it in 1..9 }, 1f,
            runnerUp.takeIf { it in 1..9 }, confidence.toFloat()).secondGuess().orEmpty()
    private fun mode(name: String): OverlayMode = when (name) {
        "Read" -> OverlayMode.READING; "Check" -> OverlayMode.CHECK; "Solve" -> OverlayMode.SOLUTION; "Hint" -> OverlayMode.HINT
        else -> OverlayMode.NONE
    }
    private fun name(mode: OverlayMode) = when (mode) {
        OverlayMode.READING -> "Read"; OverlayMode.CHECK -> "Check"; OverlayMode.SOLUTION -> "Solve"
        else -> "None"
    }
    private fun ints(values: Collection<Int>): String = values.sorted().joinToString(",", "[", "]")
    private fun array(values: List<String>): String = values.joinToString(",", "[", "]")
    private fun obj(vararg fields: Pair<String, String>): String = fields.joinToString(",", "{", "}") { jsonString(it.first) + ":" + it.second }
}
