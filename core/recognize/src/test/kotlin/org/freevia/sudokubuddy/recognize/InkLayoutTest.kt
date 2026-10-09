package org.freevia.sudokubuddy.recognize

import kotlin.test.Test
import kotlin.test.assertEquals

/** Geometric counterexamples: notes, fragmented real digits, and faint erased companions. */
class InkLayoutTest {
    private fun blob(x: Int, y: Int, w: Int, h: Int, area: Int = w * h, contrast: Double = 100.0) =
        Blob(x, y, w, h, area, w.toDouble() / h, h / 100.0, (y + h / 2.0 - 50) / 100,
            255 - contrast, contrast, area.toDouble() / maxOf(w, h), x + y * 100)

    private fun evidence(vararg blobs: Blob): InkLayout.Evidence {
        val largest = blobs.maxBy { it.area }
        return InkLayout.evidence(CellInk(largest, FloatArray(784), 0.0, blobs.size - 1, blobs.toList()))
    }

    @Test
    fun `three figures across a common baseline are notes`() {
        assertEquals(InkLayout.Evidence.NOTE_ROW, evidence(blob(8, 10, 18, 35), blob(32, 12, 22, 37), blob(60, 8, 30, 50)))
    }

    @Test
    fun `a fused candidate column retains the separate upper and lower neighbors`() {
        assertEquals(InkLayout.Evidence.STACKED_GROUP, evidence(blob(30, 8, 45, 87, 1045),
            blob(9, 3, 20, 44, 314), blob(11, 49, 30, 46, 553)))
    }

    @Test
    fun `splitting one candidate does not hide the horizontal note group`() {
        assertEquals(InkLayout.Evidence.NOTE_ROW, evidence(blob(8, 10, 18, 40), blob(32, 12, 20, 22),
            blob(32, 29, 20, 23), blob(60, 8, 26, 48)))
    }

    @Test
    fun `a full answer over faint erased candidates keeps its role`() {
        assertEquals(InkLayout.Evidence.SINGLE_GLYPH, evidence(blob(20, 5, 55, 85),
            blob(5, 10, 20, 40, contrast = 30.0), blob(7, 55, 20, 40, contrast = 25.0)))
    }

    @Test
    fun `a broken eight has a competing glyph interpretation and must not be demoted`() {
        assertEquals(InkLayout.Evidence.AMBIGUOUS_GROUP, evidence(blob(50, 10, 10, 80, 500),
            blob(25, 10, 20, 40, 300), blob(25, 51, 20, 40, 300)))
    }

    @Test
    fun `a broken single glyph whose boxes overlap is reassembled for role evidence`() {
        assertEquals(InkLayout.Evidence.SINGLE_GLYPH, evidence(blob(25, 10, 20, 60, 700),
            blob(22, 8, 25, 15, 250), blob(23, 55, 23, 20, 300)))
    }

    @Test
    fun `fusing two note rows must not erase shorter shared-row companions`() {
        assertEquals(InkLayout.Evidence.AMBIGUOUS_GROUP, evidence(blob(5, 0, 35, 92, 989),
            blob(65, 0, 23, 39, 392), blob(38, 0, 20, 22, 184), blob(38, 17, 22, 25, 181)))
    }

    @Test
    fun `tiny same-ink scraps beside a full glyph do not create a note question`() {
        assertEquals(InkLayout.Evidence.SINGLE_GLYPH, evidence(blob(10, 0, 45, 90, 1700),
            blob(65, 0, 10, 15, 120), blob(80, 0, 10, 15, 120)))
    }

    @Test
    fun `a genuine answer with two current notes receives a question rather than a note verdict`() {
        assertEquals(InkLayout.Evidence.AMBIGUOUS_GROUP, evidence(blob(5, 0, 35, 92, 1100),
            blob(44, 1, 20, 40, 400), blob(70, 2, 20, 39, 390)))
    }
}
