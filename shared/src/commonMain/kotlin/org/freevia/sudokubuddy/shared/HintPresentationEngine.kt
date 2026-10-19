package org.freevia.sudokubuddy.shared

import org.freevia.sudokubuddy.app.*
import org.freevia.sudokubuddy.model.Cell
import org.freevia.sudokubuddy.model.Grid
import org.freevia.sudokubuddy.solver.*

/** Native views consume Android's actual presentation decisions, rather than retelling them. */
class HintPresentationEngine {
    // Owned by one native worker actor. Revisiting proof frames must not solve again.
    private var cachedKey: String? = null
    private var cachedHint: Hint? = null
    fun nextDepth(showingHint: Boolean, depth: Int, explain: Boolean): Int {
        val next = PuzzleLogic.press(if (showingHint) OverlayMode.HINT else OverlayMode.NONE,
            OverlayMode.HINT, depth, if (explain) HintStyle.EXPLAIN else HintStyle.REVEAL)
        return if (next.mode == OverlayMode.NONE) -1 else next.hintDepth
    }

    fun snapshot(givens: String, progress: String, depth: Int, step: Int, consequence: Int, before: Boolean, explain: Boolean, enteredCells: String = ""): String = runCatching {
        require(givens.length == 81 && progress.length == 81)
        var grid = Grid.fromGivens(givens)
        for (index in 0 until 81) {
            val digit = progress[index].digitToIntOrNull()?.takeIf { it != 0 } ?: continue
            if (!grid[index].isFilled) grid = grid.with(index, Cell.guess(digit))
        }
        val style = if (explain) HintStyle.EXPLAIN else HintStyle.REVEAL
        val key = "$givens:$progress:$explain"
        if (cachedKey != key) { cachedHint = PuzzleLogic.hint(grid, style); cachedKey = key }
        val hint = cachedHint ?: return@runCatching obj("available" to "false")
        val visual = (hint as? Hint.Explained)?.takeIf { it.proof.isNotEmpty() && explain }
        val frame = HintFrame(step.takeIf { it >= 0 }, consequence.coerceAtLeast(0), before)
        val drawn = visual?.let { VisualHint.overlay(it, depth.coerceIn(0, 3), frame) }
            ?: PuzzleLogic.overlay(grid, OverlayMode.HINT, style, 3)
        val overlay = VisualHint.withWritten(drawn, grid, enteredCells.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0..80 }.toSet())
        val lesson = visual?.proof?.getOrNull(step)
        val chain = (lesson?.deduction as? Deduction.Elimination)?.chain
        val target = when (val move = lesson?.deduction) {
            is Deduction.Placement -> move.index
            is Deduction.Elimination -> move.fromCells.singleOrNull() ?: -1
            null -> hint.index
        }
        val elimination = (lesson?.deduction as? Deduction.Elimination)?.takeIf { depth >= 2 && it.chain == null }
        fun question(at: Int, targetCell: Int) = obj("step" to at.toString(), "label" to jsonString(VisualHint.label(visual!!, at, targetCell)))
        obj("stageNames" to array(VisualHint.stageNames.map(::jsonString)), "keyCaption" to jsonString(VisualHint.KEY_CAPTION),
            "stageCaption" to jsonString(visual?.let { VisualHint.stageCaption(it, depth) }.orEmpty()), "answerGuidance" to jsonString(VisualHint.ANSWER_GUIDANCE),
            "available" to "true", "visual" to (visual != null).toString(),
            "target" to hint.index.toString(), "digit" to hint.digit.toString(), "technique" to jsonString((hint as? Hint.Explained)?.technique.orEmpty()),
            "caption" to jsonString(visual?.let { VisualHint.caption(it, frame) } ?: (hint as? Hint.Explained)?.explanation ?: "Place ${hint.digit} in row ${hint.index / 9 + 1}, column ${hint.index % 9 + 1}."),
            "causalArrows" to array(elimination?.let { move -> VisualHint.causalTargets(move).sorted().map { target ->
                obj("target" to target.toString(), "support" to ints(move.supportingCells))
            } }.orEmpty()),
            "eliminationTargets" to ints(elimination?.fromCells.orEmpty()),
            "targets" to ints(when (val move = lesson?.deduction) { is Deduction.Elimination -> move.fromCells; is Deduction.Placement -> setOf(move.index); null -> emptySet() }),
            "elimination" to (lesson?.deduction is Deduction.Elimination).toString(),
            "lessonTechnique" to jsonString(lesson?.deduction?.technique.orEmpty()),
            "finalStep" to (visual?.proof?.lastIndex ?: -1).toString(),
            "chainLength" to (chain?.links?.size ?: -1).toString(),
            "branches" to array(visual?.let { VisualHint.branches(it).map { at -> question(at, it.index) } }.orEmpty()),
            "dependencies" to array(lesson?.dependencies?.sorted()?.map { question(it, target) }.orEmpty()),
            "digits" to array((0 until 81).map { overlay.digits[it]?.digit?.toString() ?: "0" }),
            "roles" to array((0 until 81).map { jsonString(overlay.digits[it]?.role?.name.orEmpty()) }),
            "evidence" to ints(overlay.evidence), "focus" to (overlay.focus?.toString() ?: "null"),
            "candidates" to array((0 until 81).map { ints(overlay.candidates[it].orEmpty()) }),
            "removed" to array((0 until 81).map { ints(overlay.removed[it].orEmpty()) }),
            "chain" to (overlay.chain?.let { c -> obj("links" to array(c.links.map { link ->
                obj("index" to link.index.toString(), "digit" to link.digit.toString(), "from" to (link.from?.toString() ?: "null"))
            }), "deadEndFrom" to (c.deadEndFrom?.toString() ?: "null"), "deadEnd" to ints(c.deadEnd), "blocked" to ints(c.blocked), "missing" to (c.missing?.toString() ?: "null")) } ?: "null"))
    }.getOrElse { obj("available" to "false") }

    private fun ints(values: Collection<Int>): String = values.sorted().joinToString(",", "[", "]")
    private fun array(values: List<String>): String = values.joinToString(",", "[", "]")
    private fun obj(vararg fields: Pair<String, String>): String = fields.joinToString(",", "{", "}") { jsonString(it.first) + ":" + it.second }
}
