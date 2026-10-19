package org.freevia.sudokubuddy.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeachingEngineTest {
    private val givens = "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"
    private val empty = ".".repeat(81)
    private val solution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
    private val engine = TeachingEngine()

    @Test fun fullRouteKeepsOriginalCluesAndReachesKnownSolution() {
        val route = engine.lessonJson(givens, empty, "route", "", true)
        assertTrue(route.contains("\"finishes\":true"), route.take(300))
        assertTrue(route.contains("\"solution\":\"$solution\""))
        val before = Regex("\"before\":\"([.1-9]{81})\"").findAll(route).map { it.groupValues[1] }.toList()
        val after = Regex("\"after\":\"([.1-9]{81})\"").findAll(route).map { it.groupValues[1] }.toList()
        assertEquals(51, before.size)
        assertEquals(givens, before.first()); assertEquals(solution, after.last())
        for (i in before.indices) {
            assertEquals(1, before[i].indices.count { before[i][it] != after[i][it] })
            for (cell in givens.indices) if (givens[cell] != '.') assertEquals(givens[cell], after[i][cell])
            if (i > 0) assertEquals(after[i - 1], before[i])
        }
    }
    @Test fun independentExamplesNeverAccumulateOtherExamples() {
        val catalog = engine.catalogJson(givens, empty)
        assertTrue(catalog.contains("Naked single"), catalog.take(300))
        val examples = engine.lessonJson(givens, empty, "strategy", "Naked single", true)
        val before = Regex("\"before\":\"([.1-9]{81})\"").findAll(examples).map { it.groupValues[1] }.toList()
        assertTrue(before.isNotEmpty(), examples.take(300))
        assertTrue(before.all { it == givens })
    }
    @Test fun invalidAndAmbiguousCluesDoNotExposeMisleadingProof() {
        assertTrue(engine.lessonJson("bad", empty, "route", "", true).contains("\"error\""))
        assertTrue(engine.lessonJson(empty, empty, "proof", "", true).contains("\"error\""))
        val wrong = empty.toMutableList().also { it[2] = '1' }.joinToString("")
        assertTrue(engine.lessonJson(givens, wrong, "route", "", true).contains("This reasoning excludes 1 existing"))
        assertEquals("", engine.solution("bad", empty))
    }
    @Test fun revealSolutionIsIndependentOfIncorrectUserProgress() {
        assertEquals(solution, engine.solution(givens, empty))
        val wrong = empty.toMutableList().also { it[2] = '1' }.joinToString("")
        assertEquals(solution, engine.solution(givens, wrong))
    }
}
