package org.freevia.sudokubuddy.recognize

import org.freevia.sudokubuddy.vision.CorpusFixtures
import org.freevia.sudokubuddy.vision.GateVerdict
import org.freevia.sudokubuddy.vision.OpenCvNatives
import org.freevia.sudokubuddy.vision.StructuralGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NotesAgainstAnswersCorpusTest {
    private fun read(fragment: String): ReadResult {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val gate = assertIs<GateVerdict.Usable>(StructuralGate.assess(CorpusFixtures.photo(fragment)))
        return GridReader().read(gate.cells)
    }

    private fun readings(result: ReadResult) = when (result) {
        is ReadResult.Accepted -> result.readings
        is ReadResult.NeedsConfirmation -> result.readings
        is ReadResult.Unreadable -> error(result.reason)
    }

    @Test
    fun `a full marker answer beside old candidates retains its value and questions its role`() {
        val result = assertIs<ReadResult.NeedsConfirmation>(read("newsprint-mepham-marker"))
        for ((index, digit) in listOf(15 to 7, 16 to 6, 33 to 6)) {
            val reading = result.readings[index]
            assertEquals(Ink.ANSWER, reading.ink)
            assertEquals(digit, reading.digit)
            assertTrue(reading.margin >= 0.60f, "role uncertainty must work even with a confident digit")
            assertTrue(reading.roleUncertain)
            assertTrue(index in result.uncertainCells)
            assertEquals(digit, result.grid[index].digit)
        }
    }

    @Test
    fun `a pencil candidate replaced by a full answer changes roles without losing the answer`() {
        val before = readings(read("booklet-pencil-3"))[65]
        val after = readings(read("booklet-pencil-4"))[65]
        assertEquals(Ink.MARK, before.ink)
        assertEquals(Ink.ANSWER, after.ink)
        assertEquals(4, after.digit)
        // These answers have explicitly annotated faint erased artifacts beside them.
        val all = readings(read("booklet-pencil-4"))
        for (index in listOf(39, 48, 54, 66, 79)) assertEquals(Ink.ANSWER, all[index].ink, "cell$index")
    }

    @Test
    fun `lower-line candidates stay notes even on a page with only four full answers`() {
        val twoLines = readings(read("two-line-candidates"))
        for (index in listOf(13, 22, 23)) assertEquals(Ink.MARK, twoLines[index].ink, "cell$index")
        val fewAnswers = readings(read("2026-09-09-booklet-candidates-1"))
        assertEquals(4, fewAnswers.count { it.ink == Ink.ANSWER })
        // Faint candidates can already be missed as NONE in this older corpus image;
        // this guard preserves puzzle roles, rather than inventing exhaustive note labels.
        assertEquals(53, fewAnswers.count { it.ink == Ink.MARK || it.ink == Ink.NONE })
    }
}
