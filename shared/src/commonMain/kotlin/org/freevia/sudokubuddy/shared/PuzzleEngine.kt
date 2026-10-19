package org.freevia.sudokubuddy.shared

import org.freevia.sudokubuddy.model.Cell
import org.freevia.sudokubuddy.model.Grid
import org.freevia.sudokubuddy.solver.AnswerCheck
import org.freevia.sudokubuddy.solver.AnswerChecker
import org.freevia.sudokubuddy.solver.ExplainedHintEngine
import org.freevia.sudokubuddy.solver.RevealHintEngine
import org.freevia.sudokubuddy.solver.Hint
import org.freevia.sudokubuddy.solver.SolveResult
import org.freevia.sudokubuddy.solver.Solver

/** A small Swift-facing boundary: no solver internals or mutable state cross it. */
class PuzzleEngine {
    fun evaluate(givens: String, progress: String, includeHint: Boolean, explainHints: Boolean = true): PuzzleEvaluation {
        if (!validText(givens) || !validText(progress)) {
            return PuzzleEvaluation("invalid", "Enter exactly 81 cells using digits or dots.")
        }
        var grid = Grid.fromGivens(givens)
        for (index in 0 until 81) {
            val digit = progress[index].digitToIntOrNull()?.takeIf { it != 0 } ?: continue
            if (!grid[index].isFilled) grid = grid.with(index, Cell.guess(digit))
        }
        when (Solver.solve(grid)) {
            SolveResult.None -> return PuzzleEvaluation("impossible", "The clues have no solution. Check the entered puzzle.")
            is SolveResult.Multiple -> return PuzzleEvaluation("ambiguous", "The clues allow more than one solution. Add the remaining printed clues.")
            is SolveResult.Unique -> Unit
        }
        val check = AnswerChecker.check(grid) as AnswerCheck.Checked
        val incorrect = check.incorrect.sorted().joinToString(",")
        val complete = grid.isComplete && check.incorrect.isEmpty()
        val hint = if (includeHint && check.incorrect.isEmpty()) (if (explainHints) ExplainedHintEngine.nextHint(grid) else RevealHintEngine.nextHint(grid)) else null
        return PuzzleEvaluation(
            status = if (complete) "complete" else "ready",
            message = when {
                complete -> "Puzzle complete. Every answer is correct."
                check.incorrect.isNotEmpty() -> "${check.incorrect.size} answers need another look."
                check.correct.isNotEmpty() -> "All ${check.correct.size} entered answers are correct."
                else -> "The puzzle has one solution. You're ready to play."
            },
            incorrectCells = incorrect,
            hintIndex = hint?.index ?: -1,
            hintDigit = hint?.digit ?: 0,
            hintTechnique = (hint as? Hint.Explained)?.technique ?: if (hint != null) "Reveal a cell" else "",
            hintExplanation = when (hint) {
                is Hint.Explained -> hint.fullExplanation
                is Hint.Reveal -> "Place ${hint.digit} in row ${hint.index / 9 + 1}, column ${hint.index % 9 + 1}."
                null -> ""
            },
            supportingCells = (hint as? Hint.Explained)?.supportingCells?.sorted()?.joinToString(",") ?: "",
        )
    }

    private fun validText(text: String): Boolean =
        text.length == 81 && text.all { it == '.' || it in '0'..'9' }
}

data class PuzzleEvaluation(
    val status: String,
    val message: String,
    val incorrectCells: String = "",
    val hintIndex: Int = -1,
    val hintDigit: Int = 0,
    val hintTechnique: String = "",
    val hintExplanation: String = "",
    val supportingCells: String = "",
)
