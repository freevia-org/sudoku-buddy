package org.freevia.sudokubuddy.solver

import org.freevia.sudokubuddy.model.Grid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TeachingStepTest {
    @Test
    fun `chain dependencies retain earlier removals elsewhere in hidden single units`() {
        val hint = assertIs<Hint.Explained>(ExplainedHintEngine.nextHint(Puzzles.ELIMINATION_HINT))
        val branch = hint.proof.indexOfFirst {
            val move = it.deduction
            move is Deduction.Elimination && move.digit == 4 && hint.index in move.fromCells
        }
        assertTrue(branch > 0)
        val lesson = hint.proof[branch]
        val chain = assertNotNull((lesson.deduction as Deduction.Elimination).chain)
        val premises = (chain.links.map { it.index } + chain.deadEnd).flatMap {
            org.freevia.sudokubuddy.model.Coordinates.unitsOf[it].flatten()
        }.toSet()
        for (earlier in 0 until branch) {
            if (premises.any { hint.proof[earlier].before[it] != hint.proof[earlier].after[it] }) {
                assertTrue(earlier in lesson.dependencies, "Missing earlier premise $earlier")
            }
        }
        assertTrue(0 in lesson.dependencies)
        assertTrue(19 in lesson.dependencies)
    }

    @Test
    fun `a difficult hint retains and explains the eliminations before its answer`() {
        val puzzle = Puzzles.ELIMINATION_HINT
        val hint = assertIs<Hint.Explained>(ExplainedHintEngine.nextHint(puzzle))
        assertTrue(hint.proof.size > 1, "fixture must require a prerequisite")
        assertTrue(hint.proof.dropLast(1).any { it.deduction is Deduction.Elimination })
        val solution = assertIs<SolveResult.Unique>(Solver.solve(puzzle)).solution
        val state = assertNotNull(SolverState.candidatesOnly(progressGrid(puzzle, solution)))
        for (step in hint.proof) {
            assertEquals(state.candidateSnapshot(), step.before)
            assertTrue(TechniqueSolver.apply(state, step.deduction))
            assertEquals(state.candidateSnapshot(), step.after)
            assertTrue(hint.fullExplanation.contains(step.deduction.explanation))
        }
        val answer = assertIs<Deduction.Placement>(hint.proof.last().deduction)
        assertEquals(hint.index, answer.index)
        assertEquals(hint.digit, answer.digit)
        assertTrue(hint.fullExplanation.contains("Starting candidates:"))
        assertTrue(hint.proof.last().dependencies.isNotEmpty())
    }

    @Test
    fun `every route step has a reversible candidate state and earlier proof references`() {
        for (grid in listOf(Puzzles.EASY, Puzzles.HARDEST)) {
            val route = assertNotNull(TechniqueSolver.walkthrough(grid))
            assertEquals(route.steps.size, route.lessons.size)
            val solution = assertIs<SolveResult.Unique>(Solver.solve(grid)).solution
            val state = assertNotNull(SolverState.candidatesOnly(progressGrid(grid, solution)))
            route.lessons.forEachIndexed { at, lesson ->
                assertEquals(route.steps[at], lesson.deduction)
                assertEquals(state.candidateSnapshot(), lesson.before)
                assertTrue(TechniqueSolver.apply(state, lesson.deduction))
                assertEquals(state.candidateSnapshot(), lesson.after)
                assertTrue(lesson.dependencies.all { it in 0 until at })
                if (lesson.deduction is Deduction.Elimination) {
                    for (cell in lesson.deduction.fromCells) {
                        assertTrue(lesson.deduction.digit in lesson.before.getValue(cell))
                        assertFalse(lesson.deduction.digit in lesson.after.getValue(cell))
                    }
                }
            }
        }
    }

    @Test
    fun `technique examples each start from the original candidate position`() {
        val route = assertNotNull(TechniqueSolver.findings(Puzzles.EASY, NakedSingle))
        assertFalse(route.cumulative)
        assertTrue(route.lessons.size > 1)
        assertTrue(route.lessons.all { it.before == route.lessons.first().before })
        assertTrue(route.lessons.all { it.dependencies.isEmpty() })
    }

    @Test
    fun `practice alternatives use the lesson position rather than the original board`() {
        val route = assertNotNull(TechniqueSolver.walkthrough(Puzzles.HARDEST))
        val index = route.steps.indexOfFirst { it is Deduction.Placement && it.technique == NakedSingle.name }
        assertTrue(index > 0)
        val expected = assertIs<Deduction.Placement>(route.steps[index])
        val moves = TechniqueSolver.alternativesAt(Puzzles.HARDEST, route, index)
        assertTrue(moves.filterIsInstance<Deduction.Placement>().any {
            it.index == expected.index && it.digit == expected.digit
        })
        val original = assertNotNull(SolverState.candidatesOnly(Puzzles.HARDEST))
        assertTrue(original.candidatesAt(expected.index).size > 1)
        assertEquals(emptyList(), TechniqueSolver.alternativesAt(Puzzles.HARDEST, route, 0))
    }

    @Test
    fun `a hidden single names the blockers outside its unit`() {
        val grid = Grid.fromRows(
            ".1.2.3.4.", ".........", ".........", "5........", "....5....",
            ".........", "..5......", "......5..", ".........",
        )
        val state = assertNotNull(SolverState.candidatesOnly(grid))
        val step = HiddenSingle.findAll(state).filterIsInstance<Deduction.Placement>()
            .first { it.index == 8 && it.digit == 5 }
        for (blocker in listOf(27, 40, 56, 69)) {
            assertTrue(blocker in step.supportingCells)
            assertTrue(step.explanation.contains(cellName(blocker)))
        }
        assertTrue(step.explanation.contains("row 1"))
        assertTrue(state.candidatesAt(8).size > 1)
    }
}
