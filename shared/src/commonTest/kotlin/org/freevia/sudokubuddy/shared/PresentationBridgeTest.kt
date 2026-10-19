package org.freevia.sudokubuddy.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresentationBridgeTest {
    private val clues = "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"
    private val empty = ".".repeat(81)
    @Test fun hintButtonUsesAndroidFourStageContract() {
        val engine = HintPresentationEngine()
        assertEquals(0, engine.nextDepth(false, 0, true))
        for (at in 0..3) assertEquals((at + 1).coerceAtMost(3), engine.nextDepth(true, at, true))
        assertEquals(-1, engine.nextDepth(true, 0, false))
        for (depth in 0..3) {
            val json = engine.snapshot(clues, empty, depth, -1, 0, true, true)
            assertTrue("\"available\":true" in json)
            assertTrue("\"visual\":true" in json)
            val digits = Regex("\\\"digits\\\":\\[([^]]*)]").find(json)!!.groupValues[1].split(',').map { it.toInt() }
            assertEquals(if (depth == 3) 1 else 0, digits.count { it != 0 })
        }
    }
    @Test fun layersToggleAndAmbiguousAnswersCycleWithoutModifyingProgress() {
        val engine = PuzzlePresentationEngine()
        assertTrue("\"mode\":\"None\"" in engine.snapshot(clues, empty, "Read", "Read", 0, false))
        assertTrue("\"mode\":\"None\"" in engine.snapshot(clues, empty, "Check", "Check", 0, false))
        assertTrue("\"mode\":\"None\"" in engine.snapshot(clues, empty, "Solve", "Solve", 0, false))
        val first = engine.snapshot(empty, empty, "Read", "Solve", 0, false)
        val second = engine.snapshot(empty, empty, "Solve", "Solve", 0, false)
        assertTrue("\"answerShown\":0" in first)
        assertTrue("\"answerShown\":1" in second)
        assertTrue("\"mode\":\"Solve\"" in second)
    }
    @Test fun complexProofBridgeKeepsContradictionSeparateFromRejection() {
        val grid = org.freevia.sudokubuddy.model.Grid.fromRows("8........", "..36.....", ".7..9.2..", ".5...7...", "....457..", "...1...3.", "5.1...368", "..85..91.", ".9....4..")
        val hint = org.freevia.sudokubuddy.app.PuzzleLogic.hint(grid, org.freevia.sudokubuddy.app.HintStyle.EXPLAIN) as org.freevia.sudokubuddy.solver.Hint.Explained
        val clues = (0 until 81).joinToString("") { grid[it].digit?.toString() ?: "." }
        val engine = HintPresentationEngine()
        println("BRANCHES " + org.freevia.sudokubuddy.app.VisualHint.branches(hint).joinToString { "$it: ${org.freevia.sudokubuddy.app.VisualHint.label(hint, it)}" })
        var tested = 0
        hint.proof.forEachIndexed { at, lesson ->
            val chain = (lesson.deduction as? org.freevia.sudokubuddy.solver.Deduction.Elimination)?.chain ?: return@forEachIndexed
            tested++
            val contradiction = engine.snapshot(clues, empty, 2, at, chain.links.size, false, true)
            val rejected = engine.snapshot(clues, empty, 2, at, chain.links.size + 1, false, true)
            assertTrue("Contradiction" in contradiction)
            assertTrue("assumption has been rejected" in rejected)
            assertTrue("\"chain\":null" in rejected)
        }
        assertTrue(tested > 0)
    }

    @Test fun readingConfidenceIsComparedBeforeRounding() {
        val engine = PuzzlePresentationEngine()
        assertTrue(engine.describeReading("PRINTED", 5, 0.666, 3, 0.02, false).startsWith("Not read with any confidence"))
        assertTrue(engine.describeReading("PRINTED", 5, 0.67, 3, 0.02, false).startsWith("Read as a printed"))
        assertEquals("", engine.secondGuess(0, 3, 0.5))
    }

}
