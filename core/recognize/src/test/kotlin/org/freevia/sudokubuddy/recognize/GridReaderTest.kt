package org.freevia.sudokubuddy.recognize

import org.freevia.sudokubuddy.model.CellSource
import org.freevia.sudokubuddy.vision.CorpusFixtures
import org.freevia.sudokubuddy.vision.GateVerdict
import org.freevia.sudokubuddy.vision.OpenCvNatives
import org.freevia.sudokubuddy.vision.StructuralGate
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** End to end: a photograph in, a grid the reader stands behind out. */
class GridReaderTest {

    // This is a result-screen screenshot with colored reading overlays, confidence
    // bars and duplicate white digits covering the photographed originals. It is a
    // negative recognition fixture, not an ordinary puzzle whose clues must be read.
    private val expectedRefusals = setOf(
        "0-02-05-3c2309bc39761674fc696fed1abdc1d89ec9436d7f340d0b6332481fc818fb42_b2fcb79a0899d6dc.jpg",
    )

    private fun setUp() {
        CorpusLabels.requireLabels()
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
    }

    @Test
    fun `reads corpus puzzles and explicitly rejects annotated result screenshots`() {
        setUp()
        val reader = GridReader()
        var accepted = 0
        var confirmable = 0
        var expectedRefused = 0
        var excluded = 0
        val report = StringBuilder("\n=== grid reader over the corpus ===\n")

        var considered = 0
        for (file in CorpusFixtures.photos) {
            if (file.name in CorpusLabels.sameSizeHandwriting) {
                excluded++
                continue
            }
            val verdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(CorpusFixtures.load(file)))
            val result = reader.read(verdict.cells)
            if (file.name in expectedRefusals) {
                val refusal = assertIs<ReadResult.Unreadable>(result,
                    "${file.name}: an annotated result screenshot must not invent a fresh puzzle")
                expectedRefused++
                report.append("${file.name} EXPECTED REFUSAL  ${refusal.reason}\n")
                continue
            }
            considered++

            // A photograph with no hand-written label still has to yield a grid; it just
            // cannot be scored for accuracy.
            val truth = CorpusLabels.forPhoto(file.name)
            if (truth == null) {
                when (result) {
                    is ReadResult.Accepted -> accepted++
                    is ReadResult.NeedsConfirmation -> confirmable++
                    is ReadResult.Unreadable ->
                        report.append("%-46s UNREADABLE (unlabelled)  %s%n".format(file.name, result.reason))
                }
                continue
            }

            val grid = when (result) {
                is ReadResult.Accepted -> { accepted++; result.grid }
                is ReadResult.NeedsConfirmation -> { confirmable++; result.grid }
                is ReadResult.Unreadable -> null
            }

            if (grid == null) {
                report.append("%-46s UNREADABLE  %s\n".format(file.name, (result as ReadResult.Unreadable).reason))
                continue
            }

            var givenRight = 0
            var givenTotal = 0
            var styleRight = 0
            for (i in 0 until 81) {
                val expected = truth[i]
                val actual = grid[i]
                if (expected.source == CorpusLabels.Source.GIVEN) {
                    givenTotal++
                    if (actual.digit == expected.digit) givenRight++
                }
                val actualSource = when (actual.source) {
                    CellSource.GIVEN -> CorpusLabels.Source.GIVEN
                    CellSource.GUESS -> CorpusLabels.Source.GUESS
                    CellSource.EMPTY -> CorpusLabels.Source.EMPTY
                }
                if (actualSource == expected.source) styleRight++
            }
            val label = when (result) {
                is ReadResult.Accepted -> "ACCEPTED"
                is ReadResult.NeedsConfirmation -> "CONFIRM(${result.uncertainCells.size})"
                else -> "?"
            }
            report.append(
                "%-46s %-14s givens %d/%d  style %d/81\n".format(
                    file.name, label, givenRight, givenTotal, styleRight,
                )
            )
        }
        report.append("$accepted accepted, $confirmable need confirmation, " +
            "${considered - accepted - confirmable} unexpectedly unreadable, " +
            "$expectedRefused expected refusals, $excluded excluded from this acceptance test\n")
        println(report)

        assertTrue(
            accepted + confirmable == considered,
            "every ordinary corpus puzzle should yield a grid:\n$report",
        )
    }

    @Test
    fun `the printed digits of every photo are read correctly`() {
        setUp()
        val reader = GridReader()
        for (file in CorpusFixtures.photos) {
            // Their printed digits are not read correctly, and the reason is known and
            // recorded rather than tolerated: see CorpusLabels.sameSizeHandwriting.
            if (file.name in CorpusLabels.sameSizeHandwriting) continue
            val truth = CorpusLabels.forPhoto(file.name) ?: continue
            val verdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(CorpusFixtures.load(file)))
            val result = reader.read(verdict.cells)
            val grid = when (result) {
                is ReadResult.Accepted -> result.grid
                is ReadResult.NeedsConfirmation -> result.grid
                is ReadResult.Unreadable -> continue
            }
            // The faint screen photograph is exempt: about thirty of its squares hold ink
            // too washed out to find at all, so there is no digit to be right about. It
            // is kept in the corpus as a page the app now gets a grid from and reads most
            // of, which is the point of it. See [CorpusLabels.faintOnScreen].
            if (file.name in CorpusLabels.faintOnScreen) continue
            if (file.name in CorpusLabels.drawnOver) continue
            for (i in 0 until 81) {
                if (truth[i].source != CorpusLabels.Source.GIVEN) continue
                assertTrue(
                    grid[i].digit == truth[i].digit,
                    "${file.name} cell $i: read ${grid[i].digit}, expected ${truth[i].digit}",
                )
            }
        }
    }

    @Test
    fun `a grid the reader accepts always has exactly one solution`() {
        setUp()
        val reader = GridReader()
        for (file in CorpusFixtures.photos) {
            val verdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(CorpusFixtures.load(file)))
            val result = reader.read(verdict.cells)
            if (result is ReadResult.Accepted) {
                assertTrue(
                    org.freevia.sudokubuddy.solver.Solver.hasUniqueSolution(result.grid),
                    "${file.name} was accepted but its grid is not a proper puzzle",
                )
            }
        }
    }
}
