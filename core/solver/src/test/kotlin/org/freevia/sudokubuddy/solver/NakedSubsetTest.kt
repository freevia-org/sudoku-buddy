package org.freevia.sudokubuddy.solver

import org.freevia.sudokubuddy.model.Coordinates
import org.freevia.sudokubuddy.model.Grid
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NakedSubsetTest {
    @Test
    fun `exactly enough small candidate sets still form a subset`() {
        val patterns = listOf(
            NakedPair to listOf(setOf(1, 2), setOf(1, 2)),
            NakedTriple to listOf(setOf(1, 2), setOf(2, 3), setOf(1, 3)),
            NakedQuad to listOf(setOf(1, 2), setOf(2, 3), setOf(3, 4), setOf(1, 4)),
        )
        for ((technique, candidates) in patterns) {
            for (unit in listOf(Coordinates.rowIndices[0], Coordinates.colIndices[0], Coordinates.boxIndices[0])) {
                val state = assertNotNull(SolverState.candidatesOnly(Grid.Empty))
                val group = unit.take(candidates.size).toSet()
                for ((index, digits) in group.zip(candidates)) {
                    for (digit in 1..9) if (digit !in digits) state.removeCandidate(index, digit)
                }

                val found = technique.findAll(state).filterIsInstance<Deduction.Elimination>()
                for (digit in candidates.flatten().toSet()) {
                    assertTrue(found.any {
                        it.digit == digit && it.supportingCells == group &&
                            it.fromCells == unit.toSet() - group
                    }, "${technique.name} missed $digit in $unit with exactly ${group.size} qualifying cells")
                }
            }
        }
    }
}
