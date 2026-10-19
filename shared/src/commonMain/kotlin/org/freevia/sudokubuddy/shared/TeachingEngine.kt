package org.freevia.sudokubuddy.shared

import org.freevia.sudokubuddy.app.*
import org.freevia.sudokubuddy.model.Cell
import org.freevia.sudokubuddy.model.Grid
import org.freevia.sudokubuddy.solver.*

/** Immutable JSON snapshots keep the same candidate proofs on both native interfaces. */
class TeachingEngine {
    fun catalogJson(givens: String, progress: String): String = safe {
        val grid = parse(givens, progress)
        val counts = TechniqueSolver.findingCounts(grid)
        obj("techniques" to array(Techniques.all.map { technique -> obj(
            "name" to jsonString(technique.name), "difficulty" to jsonString(technique.difficulty.name),
            "rule" to jsonString(technique.rule), "howTo" to jsonString(technique.howTo),
            "available" to (counts[technique.name]?.toString() ?: "null"),
        ) }))
    }

    fun lessonJson(givens: String, progress: String, mode: String, technique: String, shortChains: Boolean): String = safe {
        val grid = parse(givens, progress)
        val solution = (Solver.solve(grid) as? SolveResult.Unique)?.solution
            ?: return@safe obj("error" to jsonString("The printed digits do not make a solvable puzzle."))
        val route = when (mode) {
            "route" -> TechniqueSolver.walkthrough(grid, if (shortChains) RouteStyle.SHORT_CHAINS else RouteStyle.FIRST_FOUND)
            "strategy" -> Techniques.byName(technique)?.let { TechniqueSolver.findings(grid, it) }
            "proof" -> (ExplainedHintEngine.nextHint(grid) as? Hint.Explained)?.let { hint ->
                Walkthrough(hint.proof.map { it.deduction }, hint.difficulty, false,
                    hardestTechnique = hint.technique, lessons = hint.proof)
            }
            else -> null
        }
        val known = IntArray(81) { index -> grid[index].digit ?: 0 }
        val initial = known.copyOf()
        val steps = route?.steps?.mapIndexed { at, deduction ->
            if (route.cumulative.not()) initial.copyInto(known)
            val before = digits(known)
            val lesson = route.lessons.getOrNull(at)
            if (deduction is Deduction.Placement) known[deduction.index] = deduction.digit
            val after = digits(known)
            val chain = (deduction as? Deduction.Elimination)?.chain
            val guidance = PuzzleLogic.guidance(grid, OverlayMode.LESSON, HintStyle.EXPLAIN, walkthrough = route, lessonStep = at + 1)
            obj(
                "effect" to jsonString(guidance?.effect.orEmpty()),
                "overlayBefore" to overlay(PuzzleLogic.overlay(grid, OverlayMode.LESSON, HintStyle.EXPLAIN, walkthrough = route, lessonStep = at + 1, lessonBefore = true)),
                "overlayAfter" to overlay(PuzzleLogic.overlay(grid, OverlayMode.LESSON, HintStyle.EXPLAIN, walkthrough = route, lessonStep = at + 1)),
                "overlayPractice" to overlay(PuzzleLogic.overlay(grid, OverlayMode.LESSON, HintStyle.EXPLAIN, walkthrough = route, lessonStep = at + 1, concealMove = true)),
                "howTo" to jsonString(Techniques.byName(deduction.technique)?.howTo.orEmpty()),
                "technique" to jsonString(deduction.technique), "explanation" to jsonString(deduction.explanation),
                "difficulty" to jsonString(deduction.difficulty.name), "hasProof" to deduction.hasTeachingProof.toString(),
                "index" to ((deduction as? Deduction.Placement)?.index ?: -1).toString(),
                "digit" to when (deduction) { is Deduction.Placement -> deduction.digit; is Deduction.Elimination -> deduction.digit }.toString(),
                "supporting" to ints(deduction.supportingCells),
                "removed" to ints((deduction as? Deduction.Elimination)?.fromCells.orEmpty()),
                "before" to jsonString(before), "after" to jsonString(after),
                "beforeCandidates" to candidates(lesson?.before.orEmpty()), "afterCandidates" to candidates(lesson?.after.orEmpty()),
                "dependencies" to ints(lesson?.dependencies.orEmpty()),
                "dependencyLinks" to array(lesson?.dependencies?.sorted()?.map { dependency ->
                    obj("step" to dependency.toString(), "label" to jsonString(PuzzleLogic.dependencyLabel(route.steps.getOrNull(dependency), (deduction as? Deduction.Placement)?.index, dependency)))
                }.orEmpty()),
                "chain" to (chain?.let { c -> obj("links" to array(c.links.map { link ->
                    obj("index" to link.index.toString(), "digit" to link.digit.toString(), "from" to (link.from?.toString() ?: "null"))
                }), "deadEndFrom" to (c.deadEndFrom?.toString() ?: "null"), "deadEnd" to ints(c.deadEnd), "blocked" to ints(c.blocked), "missing" to (c.missing?.toString() ?: "null")) } ?: "null"),
            )
        }.orEmpty()
        val counts = TechniqueSolver.findingCounts(grid)
        obj("available" to array(Techniques.all.filter { (counts[it.name] ?: 0) > 0 }.map { obj("name" to jsonString(it.name), "count" to counts.getValue(it.name).toString()) }),
            "reasoningNote" to jsonString(PuzzleLogic.reasoningNote(grid).orEmpty()), "introduction" to jsonString(PuzzleLogic.guidance(grid, OverlayMode.LESSON, HintStyle.EXPLAIN, walkthrough = route, lessonStep = 0, technique = technique.takeIf { it.isNotEmpty() })?.body.orEmpty()),
            "chapters" to array(PuzzleLogic.chapters(route).map { chapter -> obj("technique" to jsonString(chapter.technique), "from" to chapter.from.toString(), "count" to chapter.count.toString()) }),
            "steps" to array(steps), "solution" to jsonString(digits(IntArray(81) { solution[it].digit ?: 0 })),
            "finishes" to (route?.finishes ?: false).toString(), "cumulative" to (route?.cumulative ?: true).toString(),
            "hardest" to jsonString(route?.hardestTechnique ?: ""), "triedOut" to (route?.triedOut ?: 0).toString())
    }

    fun practiceJson(givens: String, progress: String, mode: String, technique: String, shortChains: Boolean, step: Int, cell: Int, digit: Int): String = safe {
        val grid = parse(givens, progress)
        val route = when (mode) {
            "route" -> TechniqueSolver.walkthrough(grid, if (shortChains) RouteStyle.SHORT_CHAINS else RouteStyle.FIRST_FOUND)
            "strategy" -> Techniques.byName(technique)?.let { TechniqueSolver.findings(grid, it) }
            "proof" -> (ExplainedHintEngine.nextHint(grid) as? Hint.Explained)?.let { hint ->
                Walkthrough(hint.proof.map { it.deduction }, hint.difficulty, false, hardestTechnique = hint.technique, lessons = hint.proof)
            }
            else -> null
        } ?: return@safe obj("correct" to "false", "message" to jsonString("This puzzle could not be prepared."))
        val move = route.steps.getOrNull(step) ?: return@safe obj("correct" to "false", "message" to jsonString("This puzzle could not be prepared."))
        val result = org.freevia.sudokubuddy.app.PuzzleLogic.practiceAnswer(move, cell, digit,
            route.lessons.getOrNull(step)?.before.orEmpty(), TechniqueSolver.alternativesAt(grid, route, step))
        obj("correct" to result.correct.toString(), "message" to jsonString(result.message))
    }

    fun solution(givens: String, progress: String): String = runCatching {
        val solved = Solver.solve(parse(givens, progress)) as? SolveResult.Unique ?: return@runCatching ""
        digits(IntArray(81) { solved.solution[it].digit ?: 0 })
    }.getOrDefault("")

    private fun overlay(value: Overlay): String = obj(
        "legend" to array(PuzzleLogic.legend(value, OverlayMode.LESSON, false).map { jsonString(it.name) }),
        "digits" to array((0 until 81).map { value.digits[it]?.digit?.toString() ?: "0" }),
        "roles" to array((0 until 81).map { jsonString(value.digits[it]?.role?.name.orEmpty()) }),
        "evidence" to ints(value.evidence), "focus" to (value.focus?.toString() ?: "null"),
        "candidates" to candidates(value.candidates), "removed" to array((0 until 81).map { ints(value.removed[it].orEmpty()) }),
        "chain" to (value.chain?.let { c -> obj("links" to array(c.links.map { link ->
            obj("index" to link.index.toString(), "digit" to link.digit.toString(), "from" to (link.from?.toString() ?: "null"))
        }), "deadEndFrom" to (c.deadEndFrom?.toString() ?: "null"), "deadEnd" to ints(c.deadEnd), "blocked" to ints(c.blocked), "missing" to (c.missing?.toString() ?: "null")) } ?: "null"),
    )

    private fun parse(givens: String, progress: String): Grid {
        require(listOf(givens, progress).all { it.length == 81 && it.all { c -> c == '.' || c in '0'..'9' } })
        var grid = Grid.fromGivens(givens)
        for (index in 0 until 81) {
            val digit = progress[index].digitToIntOrNull()?.takeIf { it != 0 } ?: continue
            if (!grid[index].isFilled) grid = grid.with(index, Cell.guess(digit))
        }
        return grid
    }
    private fun safe(block: () -> String): String = runCatching(block).getOrElse { obj("error" to jsonString("This puzzle could not be prepared.")) }
    private fun candidates(values: Map<Int, List<Int>>): String = array((0 until 81).map { ints(values[it].orEmpty()) })
    private fun digits(values: IntArray): String = values.joinToString("") { if (it == 0) "." else it.toString() }
    private fun ints(values: Collection<Int>): String = values.sorted().joinToString(",", "[", "]")
    private fun array(values: List<String>): String = values.joinToString(",", "[", "]")
    private fun obj(vararg fields: Pair<String, String>): String = fields.joinToString(",", "{", "}") { jsonString(it.first) + ":" + it.second }
}
