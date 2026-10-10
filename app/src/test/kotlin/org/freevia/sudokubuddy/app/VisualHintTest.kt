package org.freevia.sudokubuddy.app

import org.freevia.sudokubuddy.model.Coordinates
import org.freevia.sudokubuddy.model.Grid
import org.freevia.sudokubuddy.solver.Deduction
import org.freevia.sudokubuddy.solver.Hint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VisualHintTest {
    @Test fun `wrong written answers cannot repaint hypothetical digits or contradiction walls`() {
        val grid = Grid.Empty.with(37, org.freevia.sudokubuddy.model.Cell.guess(9))
            .with(25, org.freevia.sudokubuddy.model.Cell.guess(9))
            .with(0, org.freevia.sudokubuddy.model.Cell.guess(8))
            .with(1, org.freevia.sudokubuddy.model.Cell.given(4))
        val overlay = Overlay(emptyMap(), emptySet(), chain = org.freevia.sudokubuddy.solver.Chain(
            listOf(org.freevia.sudokubuddy.solver.ChainLink(37, 3)), setOf(25)))
        val result = VisualHint.withWritten(overlay, grid, setOf(37, 25, 0, 1))
        assertEquals(setOf(0), result.digits.keys)
    }

    private val hint = assertIs<Hint.Explained>(PuzzleLogic.hint(Grid.fromRows(
        "8........", "..36.....", ".7..9.2..", ".5...7...", "....457..",
        "...1...3.", "5.1...368", "..85..91.", ".9....4..",
    ), HintStyle.EXPLAIN))

    @Test fun `hint levels stay at the destination and reveal the answer only at the end`() {
        for (depth in 0..3) {
            val view = VisualHint.overlay(hint, depth, HintFrame())
            assertEquals(hint.index, view.focus)
            assertEquals(if (depth == 3) setOf(hint.index) else emptySet(), view.digits.keys)
            if (depth == 1) assertEquals(hint.proof.first().before[hint.index], view.candidates[hint.index])
            if (depth > 0) assertTrue(view.evidence.isEmpty())
        }
        assertEquals(hint.digit, VisualHint.overlay(hint, 3, HintFrame()).digits[hint.index]?.digit)
    }

    @Test fun `target questions correspond to actual removals and are not the first unrelated deduction`() {
        val branches = VisualHint.branches(hint)
        assertTrue(branches.isNotEmpty())
        assertTrue(branches.first() > 0)
        val removed = branches.map { step ->
            val move = assertIs<Deduction.Elimination>(hint.proof[step].deduction)
            assertTrue(hint.index in move.fromCells)
            assertTrue(move.digit in hint.proof[step].before.getValue(hint.index))
            assertFalse(move.digit in hint.proof[step].after.getValue(hint.index))
            move.digit
        }.toSet()
        assertEquals(hint.proof.first().before.getValue(hint.index).toSet() - hint.digit, removed)
    }

    @Test fun `nested questions return to the exact prior before after and chain frame`() {
        val start = HintNavigation(HintFrame(step = 23, consequence = 4, before = false))
        val nested = start.visit(16).visit(9).visit(0)
        assertEquals(start, nested.back().back().back())
        assertEquals(HintNavigation(), HintNavigation().back())
    }

    @Test fun `every chain frame displays only derived links and postpones contradiction and removal`() {
        hint.proof.forEachIndexed { step, lesson ->
            val move = lesson.deduction as? Deduction.Elimination ?: return@forEachIndexed
            val chain = move.chain ?: return@forEachIndexed
            val candidates = lesson.before.toMutableMap()
            for (at in chain.links.indices) {
                val frame = HintFrame(step, at, before = false)
                val view = VisualHint.overlay(hint, 2, frame)
                assertEquals(chain.links.take(at + 1), view.chain?.links)
                assertTrue(view.chain!!.deadEnd.isEmpty())
                assertTrue(view.removed.isEmpty())
                assertTrue(view.digits.isEmpty())
                val link = chain.links[at]
                if (at > 0) {
                    val naked = candidates[link.index] == listOf(link.digit)
                    val hidden = Coordinates.unitsOf[link.index].any { unit ->
                        unit.filter { link.digit in candidates[it].orEmpty() } == listOf(link.index)
                    }
                    assertTrue(naked || hidden, "Unsupported hypothetical step $step / $at")
                    assertTrue(VisualHint.caption(hint, frame).contains(if (naked) "only one candidate" else "only remaining place"),
                        "Incorrect caption $step / $at: ${VisualHint.caption(hint, frame)}")
                }
                candidates.remove(link.index)
                for (peer in Coordinates.peers[link.index]) candidates[peer]?.let {
                    candidates[peer] = it - link.digit
                }
                for ((cell, digits) in view.candidates) {
                    assertEquals(candidates[cell], digits, "Stale candidates at step $step / $at / cell $cell")
                }
            }
            val final = VisualHint.overlay(hint, 2, HintFrame(step, chain.links.size, before = false))
            assertEquals(chain, final.chain)
            assertTrue(final.removed.isEmpty())
            assertTrue(VisualHint.caption(hint, HintFrame(step, chain.links.size, false)).contains("Contradiction"))
            val rejected = VisualHint.overlay(hint, 2, HintFrame(step, chain.links.size + 1, before = false))
            assertEquals(null, rejected.chain)
            assertEquals(move.fromCells, rejected.removed.keys)
        }
    }
}
