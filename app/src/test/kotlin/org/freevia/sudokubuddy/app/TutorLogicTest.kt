package org.freevia.sudokubuddy.app

import org.freevia.sudokubuddy.model.Cell
import org.freevia.sudokubuddy.model.Grid
import org.freevia.sudokubuddy.solver.Deduction
import org.freevia.sudokubuddy.solver.Hint
import org.freevia.sudokubuddy.solver.SolveResult
import org.freevia.sudokubuddy.solver.Solver
import org.freevia.sudokubuddy.solver.TechniqueSolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TutorLogicTest {
    private val puzzle = Grid.fromRows(
        "8........", "..36.....", ".7..9.2..", ".5...7...", "....457..",
        "...1...3.", "5.1...368", "..85..91.", ".9....4..",
    )

    @Test
    fun `before and after show the actual candidate elimination and previous moves`() {
        val route = assertNotNull(TechniqueSolver.walkthrough(puzzle))
        val at = route.steps.indexOfFirst { it is Deduction.Elimination }
        assertTrue(at >= 0)
        val step = assertIs<Deduction.Elimination>(route.steps[at])
        val before = PuzzleLogic.overlay(puzzle, OverlayMode.LESSON, HintStyle.EXPLAIN,
            walkthrough = route, lessonStep = at + 1, lessonBefore = true)
        val after = PuzzleLogic.overlay(puzzle, OverlayMode.LESSON, HintStyle.EXPLAIN,
            walkthrough = route, lessonStep = at + 1)
        assertTrue(before.removed.isEmpty())
        assertEquals(step.fromCells, after.removed.keys)
        for (cell in step.fromCells) {
            assertTrue(step.digit in before.candidates.getValue(cell))
            assertFalse(step.digit in after.candidates.getValue(cell))
            assertEquals(setOf(step.digit), after.removed[cell])
        }
        assertTrue(LegendKey.REMOVED in PuzzleLogic.legend(after, OverlayMode.LESSON, false))
        assertEquals(before, PuzzleLogic.overlay(puzzle, OverlayMode.LESSON, HintStyle.EXPLAIN,
            walkthrough = route, lessonStep = at + 1, lessonBefore = true))
    }

    @Test
    fun `practice shows the starting position without revealing the move or its chain`() {
        val route = assertNotNull(TechniqueSolver.walkthrough(puzzle))
        val overlay = PuzzleLogic.overlay(puzzle, OverlayMode.LESSON, HintStyle.EXPLAIN,
            walkthrough = route, lessonStep = 1, concealMove = true)
        assertTrue(overlay.digits.isEmpty())
        assertTrue(overlay.evidence.isEmpty())
        assertTrue(overlay.removed.isEmpty())
        assertNull(overlay.focus)
        assertNull(overlay.chain)
        assertEquals(route.lessons.first().before, overlay.candidates)
    }

    @Test
    fun `a placement appears only after the move and earlier placements survive going back`() {
        val route = assertNotNull(TechniqueSolver.walkthrough(puzzle))
        val at = route.steps.indexOfFirst { it is Deduction.Placement }
        val step = assertIs<Deduction.Placement>(route.steps[at])
        val before = PuzzleLogic.overlay(puzzle, OverlayMode.LESSON, HintStyle.EXPLAIN,
            walkthrough = route, lessonStep = at + 1, lessonBefore = true)
        val after = PuzzleLogic.overlay(puzzle, OverlayMode.LESSON, HintStyle.EXPLAIN,
            walkthrough = route, lessonStep = at + 1)
        assertFalse(step.index in before.digits)
        assertEquals(step.digit, after.digits.getValue(step.index).digit)
        assertEquals(before.digits + (step.index to after.digits.getValue(step.index)), after.digits)
    }

    @Test
    fun `final hint includes the complete proof rather than just the last placement`() {
        val hint = assertIs<Hint.Explained>(PuzzleLogic.hint(puzzle, HintStyle.EXPLAIN))
        assertTrue(hint.proof.size > 1)
        val guidance = assertNotNull(PuzzleLogic.guidance(puzzle, OverlayMode.HINT, HintStyle.EXPLAIN))
        for (step in hint.proof) assertTrue(guidance.body.contains(step.deduction.explanation))
        assertTrue(guidance.body.contains("Place ${hint.digit} in row"))
        assertTrue(guidance.body.contains("Starting candidates:"))
    }

    @Test
    fun `practice accepts each elimination target and gives candidate feedback for impossible answers`() {
        val route = assertNotNull(TechniqueSolver.walkthrough(puzzle))
        val step = assertIs<Deduction.Elimination>(route.steps.first())
        val before = route.lessons.first().before
        for (cell in step.fromCells) {
            assertTrue(PuzzleLogic.practiceAnswer(step, cell, step.digit, before).correct)
        }
        val impossible = before.entries.first { it.value.size < 9 }
        val absent = (1..9).first { it !in impossible.value }
        val feedback = PuzzleLogic.practiceAnswer(step, impossible.key, absent, before)
        assertFalse(feedback.correct)
        assertTrue(feedback.message.contains("already ruled out"))
        assertTrue(feedback.message.contains(impossible.value.joinToString(", ", "{", "}")))
        assertFalse(PuzzleLogic.practiceAnswer(step, 0, 8, before).correct)
    }

    @Test
    fun `the tutor explains when it excludes incorrect entries`() {
        assertNull(PuzzleLogic.reasoningNote(puzzle))
        val truth = assertIs<SolveResult.Unique>(Solver.solve(puzzle)).solution
        val cell = (0 until 81).first { !puzzle[it].isFilled }
        val wrong = (1..9).first { it != truth[cell].digit }
        val spoiled = puzzle.with(cell, Cell.guess(wrong))
        assertTrue(assertNotNull(PuzzleLogic.reasoningNote(spoiled)).contains("excludes 1 existing entry"))
        assertNull(PuzzleLogic.reasoningNote(puzzle.with(cell, Cell.guess(truth[cell].digit!!))))
    }

    @Test
    fun `practice recognizes a valid alternative without changing the lesson route`() {
        val easy = Grid.fromRows(
            "53..7....", "6..195...", ".98....6.", "8...6...3", "4..8.3..1",
            "7...2...6", ".6....28.", "...419..5", "....8..79",
        )
        val route = assertNotNull(TechniqueSolver.walkthrough(easy))
        val illustrated = assertIs<Deduction.Placement>(route.steps.first())
        val alternatives = TechniqueSolver.alternativesAt(easy, route, 0)
        val alternative = alternatives.filterIsInstance<Deduction.Placement>()
            .first { it.index != illustrated.index }
        val result = PuzzleLogic.practiceAnswer(illustrated, alternative.index, alternative.digit,
            route.lessons.first().before, alternatives)
        assertTrue(result.correct)
        assertTrue(result.message.contains("also a valid"))
        assertTrue(result.message.contains("different move"))
        assertEquals(illustrated, route.steps.first())
        assertFalse(easy[alternative.index].isFilled)
    }
}
