package org.freevia.sudokubuddy.solver

import org.freevia.sudokubuddy.model.Coordinates

/** Immutable board states let hints and lessons replay the same proof without solving again. */
data class TeachingStep(
    val deduction: Deduction,
    val before: Map<Int, List<Int>>,
    val after: Map<Int, List<Int>>,
    /** Zero-based earlier steps whose candidate changes this deduction uses. */
    val dependencies: Set<Int>,
)

internal fun SolverState.candidateSnapshot(): Map<Int, List<Int>> =
    (0 until Coordinates.CELL_COUNT).filter { !isReported(it) }
        .associateWith { candidatesAt(it).digits() }

/** Record every prerequisite, including eliminations that do not fill a square. */
internal fun teachingStep(
    state: SolverState,
    deduction: Deduction,
    previous: List<TeachingStep>,
): TeachingStep? {
    val before = state.candidateSnapshot()
    val affected = deduction.supportingCells + when (deduction) {
        is Deduction.Placement -> setOf(deduction.index)
        is Deduction.Elimination -> deduction.fromCells
    }
    val dependencies = previous.indices.filterTo(mutableSetOf()) { at ->
        affected.any { previous[at].before[it] != previous[at].after[it] }
    }
    if (!TechniqueSolver.apply(state, deduction)) return null
    return TeachingStep(deduction, before, state.candidateSnapshot(), dependencies)
}

internal fun cellName(index: Int): String = "r${index / 9 + 1}c${index % 9 + 1}"

internal fun sharedUnit(first: Int, second: Int): String = when {
    first / 9 == second / 9 -> "row ${first / 9 + 1}"
    first % 9 == second % 9 -> "column ${first % 9 + 1}"
    else -> "box ${Coordinates.boxOf(first) + 1}"
}

internal fun candidateList(digits: List<Int>): String = digits.joinToString(", ", "{", "}")
