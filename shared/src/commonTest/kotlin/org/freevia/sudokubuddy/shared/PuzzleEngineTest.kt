package org.freevia.sudokubuddy.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PuzzleEngineTest {
    private val puzzle = "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"
    private val empty = ".".repeat(81)
    private val engine = PuzzleEngine()

    @Test fun rejectsMalformedInput() {
        assertEquals("invalid", engine.evaluate("123", empty, false).status)
        assertEquals("invalid", engine.evaluate(puzzle, "x".repeat(81), false).status)
    }

    @Test fun distinguishesImpossibleAndAmbiguousClues() {
        assertEquals("ambiguous", engine.evaluate(empty, empty, false).status)
        assertEquals("impossible", engine.evaluate("55" + puzzle.drop(2), empty, false).status)
    }

    @Test fun checksGuessesWithoutChangingTheGivens() {
        val wrong = empty.toMutableList().also { it[2] = '1' }.joinToString("")
        val result = engine.evaluate(puzzle, wrong, true)
        assertEquals("ready", result.status)
        assertEquals("2", result.incorrectCells)
        assertEquals(-1, result.hintIndex)
        // A progress value placed over a printed clue must not replace it.
        assertEquals("ready", engine.evaluate(puzzle, "1" + empty.drop(1), false).status)
    }

    @Test fun producesAnExplainedHintAndAcceptsItsAnswer() {
        val result = engine.evaluate(puzzle, empty, true)
        assertTrue(result.hintIndex in 0..80)
        assertTrue(result.hintDigit in 1..9)
        assertTrue(result.hintExplanation.isNotBlank())
        val progress = empty.toMutableList().also {
            it[result.hintIndex] = result.hintDigit.digitToChar()
        }.joinToString("")
        assertEquals("", engine.evaluate(puzzle, progress, false).incorrectCells)
    }

    @Test fun recognizesCompletedPuzzle() {
        val solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
        assertEquals("complete", engine.evaluate(puzzle, solution, false).status)
    }
}
