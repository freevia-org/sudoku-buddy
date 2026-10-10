package org.freevia.sudokubuddy.app

import org.freevia.sudokubuddy.model.Coordinates
import org.freevia.sudokubuddy.solver.Deduction
import org.freevia.sudokubuddy.solver.Hint

/** The stack stores both the question and the exact frame to return to. */
data class HintFrame(val step: Int? = null, val consequence: Int = 0, val before: Boolean = true)
data class HintNavigation(val frame: HintFrame = HintFrame(), val parents: List<HintFrame> = emptyList()) {
    fun visit(step: Int) = HintNavigation(HintFrame(step), parents + frame)
    fun back() = if (parents.isEmpty()) this else HintNavigation(parents.last(), parents.dropLast(1))
}

internal object VisualHint {
    fun withWritten(overlay: Overlay, grid: org.freevia.sudokubuddy.model.Grid, entered: Set<Int>): Overlay {
        val teachingCells = overlay.candidates.keys + overlay.chain?.links.orEmpty().map { it.index } +
            setOfNotNull(overlay.chain?.deadEnd?.singleOrNull())
        val written = (entered - teachingCells).filter { grid[it].source != org.freevia.sudokubuddy.model.CellSource.GIVEN }
            .mapNotNull { index -> grid[index].digit?.let { digit ->
            index to OverlayDigit(digit, OverlayRole.WRITTEN)
        } }.toMap()
        return overlay.copy(digits = written + overlay.digits)
    }

    private fun units(cell: Int): List<List<Int>> = listOf(
        (0..8).map { cell / 9 * 9 + it }, (0..8).map { it * 9 + cell % 9 },
        Coordinates.boxIndices[Coordinates.boxOf(cell)].toList(),
    )

    /** Candidate state immediately before the currently revealed hypothetical link. */
    private fun hypotheticalBefore(hint: Hint.Explained, frame: HintFrame): Map<Int, List<Int>> {
        val lesson = hint.proof.getOrNull(frame.step ?: -1) ?: return emptyMap()
        val chain = (lesson.deduction as? Deduction.Elimination)?.chain ?: return lesson.before
        val candidates = lesson.before.toMutableMap()
        for (link in chain.links.take(frame.consequence)) {
            candidates.remove(link.index)
            for (peer in units(link.index).flatten().toSet()) {
                candidates[peer]?.let { candidates[peer] = it - link.digit }
            }
        }
        return candidates
    }

    private fun forcingUnit(hint: Hint.Explained, frame: HintFrame): List<Int>? {
        val chain = (hint.proof.getOrNull(frame.step ?: -1)?.deduction as? Deduction.Elimination)?.chain ?: return null
        val link = chain.links.getOrNull(frame.consequence) ?: return null
        if (frame.consequence == 0) return null
        val candidates = hypotheticalBefore(hint, frame)
        if (candidates[link.index]?.size == 1) return null
        return units(link.index).firstOrNull { unit ->
            unit.filter { link.digit in candidates[it].orEmpty() } == listOf(link.index)
        }
    }
    fun branches(hint: Hint.Explained): List<Int> = hint.proof.indices.filter {
        val move = hint.proof[it].deduction
        move is Deduction.Elimination && hint.index in move.fromCells
    }

    fun label(hint: Hint.Explained, step: Int, target: Int = hint.index): String {
        val move = hint.proof[step].deduction
        return if (move is Deduction.Elimination && target in move.fromCells) {
            "Why not ${move.digit}? · ${move.technique}"
        } else {
            val cells = when (move) {
                is Deduction.Elimination -> move.fromCells
                is Deduction.Placement -> setOf(move.index)
            }
            val first = cells.min()
            val effect = if (move is Deduction.Elimination) "Remove ${move.digit}" else "Place ${(move as Deduction.Placement).digit}"
            "$effect at row ${first / 9 + 1}, column ${first % 9 + 1}${if (cells.size > 1) " and ${cells.size - 1} more" else ""} · ${move.technique}"
        }
    }

    fun overlay(hint: Hint.Explained, depth: Int, frame: HintFrame): Overlay {
        val target = hint.index
        val initial = hint.proof.firstOrNull()?.before?.get(target).orEmpty()
        val lesson = if (depth >= 2) frame.step?.let { hint.proof.getOrNull(it) } else null
        if (lesson == null) {
            return Overlay(
                digits = if (depth == 3) mapOf(target to OverlayDigit(hint.digit, OverlayRole.HINT)) else emptyMap(),
                evidence = if (depth == 0) Coordinates.boxIndices[Coordinates.boxOf(target)].toSet() - target else emptySet(),
                focus = target,
                candidates = if (depth in 1..2) mapOf(target to initial) else emptyMap(),
            )
        }
        val move = lesson.deduction
        val targets = when (move) {
            is Deduction.Elimination -> move.fromCells
            is Deduction.Placement -> setOf(move.index)
        }
        val chain = (move as? Deduction.Elimination)?.chain
        val completed = chain == null || frame.consequence > chain.links.size
        val after = !frame.before && completed
        val snapshot = if (after) lesson.after else if (chain != null && !frame.before) {
            hypotheticalBefore(hint, frame.copy(consequence = (frame.consequence + 1).coerceAtMost(chain.links.size)))
        } else lesson.before
        val unitEvidence = if (chain != null && !frame.before && !completed) forcingUnit(hint, frame).orEmpty().toSet() else emptySet()
        // Frozen snapshots retain all prerequisites; this is browsing a proof, not
        // replaying a pruned route from scratch.
        val shownChain = if (frame.before) null else chain?.let {
            if (completed) null else if (frame.consequence == it.links.size) it else it.copy(links = it.links.take(frame.consequence + 1),
                deadEnd = emptySet(), missing = null, deadEndFrom = null, blocked = emptySet())
        }
        return Overlay(
            digits = if (after && move is Deduction.Placement) {
                mapOf(move.index to OverlayDigit(move.digit, OverlayRole.HINT))
            } else emptyMap(),
            evidence = if (chain == null || after) move.supportingCells else if (frame.before) targets else unitEvidence,
            focus = targets.singleOrNull(),
            chain = shownChain,
            candidates = snapshot.filterKeys { it in move.supportingCells + targets + target + unitEvidence },
            removed = if (after && move is Deduction.Elimination) targets.associateWith { setOf(move.digit) } else emptyMap(),
        )
    }

    fun caption(hint: Hint.Explained, frame: HintFrame): String {
        val lesson = frame.step?.let { hint.proof.getOrNull(it) } ?: return "Choose a candidate to see why it cannot go in your square."
        val move = lesson.deduction
        val chain = (move as? Deduction.Elimination)?.chain
        if (chain == null) return move.explanation.replace(Regex("r(\\d)c(\\d)"), "row $1, column $2")
        if (frame.before) return "Test ${move.digit} as an assumption in the marked square. It is not an answer. Follow the consequences to see why it fails."
        if (frame.consequence > chain.links.size) return "The assumption has been rejected. We return to the real candidates and cross out ${move.digit} in the marked square. The hypothetical digits are cleared."
        if (frame.consequence >= chain.links.size) return if (chain.missing == null) {
            "Contradiction: the red square has no candidate left. This is impossible, so the starting assumption must fail."
        } else "Contradiction: the red ${chain.deadEndUnit} has nowhere left for ${chain.missing}. This is impossible, so the starting assumption must fail."
        val link = chain.links[frame.consequence]
        if (frame.consequence == 0) return "Suppose the marked square holds ${link.digit}. The numbered digits are hypothetical."
        val unit = forcingUnit(hint, frame)
        val why = if (unit == null) "Earlier hypothetical placements leave it with only one candidate." else {
            val name = when {
                unit.all { it / 9 == link.index / 9 } -> "row"
                unit.all { it % 9 == link.index % 9 } -> "column"
                else -> "box"
            }
            "It is the only remaining place for ${link.digit} in the highlighted $name."
        }
        return "${link.digit} is forced in the newest numbered square (row ${link.index / 9 + 1}, column ${link.index % 9 + 1}). $why The arrow follows a contributing placement; earlier consequences also matter."
    }
}
