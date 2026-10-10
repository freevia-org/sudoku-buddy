package org.freevia.sudokubuddy.app

import org.freevia.sudokubuddy.model.Cell
import org.freevia.sudokubuddy.model.Grid
import org.freevia.sudokubuddy.recognize.Ink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HistoryDetailsTest {
    @Test
    fun `corrections questions and fitted geometry survive reopening`() {
        val grid = Grid.Empty.with(40, Cell.guess(7))
        val details = HistoryDetails(entered = setOf(40), uncertain = setOf(8, 80),
            lines = GridLines(List(10) { -0.02f + it / 9f * 1.05f }, List(10) { 0.01f + it / 9f * 0.98f }),
            reports = List(81) { if (it == 40) null else CellReport(Ink.PRINTED, 3, 0.6f, 8, 0.3f, roleUncertain = it == 8) },
            framingNote = "Move closer\nCheck the grid", readerComplaint = "Check: 3 or 8?")
        val saved = HistoryFormat.encode(grid, details)
        assertEquals(grid, HistoryFormat.decode(saved))
        assertEquals(details, HistoryFormat.details(saved))
    }

    @Test
    fun `original reading and every correction survive reopening`() {
        val original = Grid.Empty.with(0, Cell.given(3))
        val correction = ReadingCorrection(0, 3, 8, "GIVEN", "GIVEN", 1234)
        val details = HistoryDetails(
            originalGrid = original,
            originalReports = List(81) { if (it == 0) CellReport(Ink.PRINTED, 3, 0.9f, 8, 0.1f) else null },
            originalUncertain = setOf(0),
            corrections = listOf(correction),
            submittedCorrectionCount = 0,
            receipts = listOf(SubmissionReceipt("a".repeat(64), "accepted", 2345)),
            trainingConsent = true,
            trainIfAutoShared = true,
        )
        val saved = HistoryFormat.encode(original.with(0, Cell.given(8)), details)
        val restored = HistoryFormat.details(saved)

        assertEquals(original, restored.originalGrid)
        assertEquals(3, restored.originalReports?.get(0)?.digit)
        assertEquals(setOf(0), restored.originalUncertain)
        assertEquals(listOf(correction), restored.corrections)
        assertEquals(0, restored.submittedCorrectionCount)
        assertEquals(details.receipts, restored.receipts)
        assertEquals(true, restored.trainingConsent)
        assertEquals(true, restored.trainIfAutoShared)
    }

    @Test
    fun `legacy reports default to a settled role while malformed role flags are rejected`() {
        val reports = List(81) { CellReport(Ink.ANSWER, 4, 0.99f, 8, 0.01f) }
        val current = HistoryDetails(reports = reports).encode()
        val legacy = current.replace(",false", "")
        assertEquals(reports, HistoryDetails.decode(legacy).reports)
        assertFailsWith<IllegalArgumentException> {
            HistoryDetails.decode(current.replace(",false", ",unknown"))
        }
    }

    @Test
    fun `legacy and damaged metadata preserve readable grids`() {
        val grid = Grid.Empty.with(0, Cell.given(5))
        val legacy = HistoryFormat.encode(grid)
        assertEquals(HistoryDetails(), HistoryFormat.details(legacy))
        val damaged = legacy + "\n==details==\nentered=100\n"
        assertEquals(grid, HistoryFormat.decode(damaged))
        assertEquals(HistoryDetails(), HistoryFormat.details(damaged))
        assertEquals(grid, HistoryFormat.decode(legacy.replace("\n", "\r\n")))
    }

    @Test
    fun `legacy reading defaults to analysis only and never inherits training consent`() {
        val grid = Grid.Empty.with(0, Cell.given(5))
        val legacy = HistoryFormat.encode(grid, HistoryDetails())
            .replace("trainingConsent=false\n", "")
            .replace("trainIfAutoShared=false\n", "")

        val restored = HistoryFormat.details(legacy)
        assertEquals(false, restored.trainingConsent)
        assertEquals(false, restored.trainIfAutoShared)
        assertEquals(HistoryDetails(), restored)
    }

    @Test
    fun `invalid geometry and cell indices cannot reach the overlay`() {
        val valid = HistoryDetails().encode()
        assertFailsWith<IllegalArgumentException> {
            HistoryDetails.decode(valid.replace("entered=", "entered=81"))
        }
        assertFailsWith<IllegalArgumentException> {
            HistoryDetails.decode(valid.replace("vertical=0.0,", "vertical=NaN,"))
        }
    }
}
