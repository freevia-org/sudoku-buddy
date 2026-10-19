package org.freevia.sudokubuddy.recognize

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.freevia.sudokubuddy.vision.RgbImage

class ResultOverlayGuardTest {
    private fun tile(whiteGlyph: Boolean, coloredPaper: Boolean = true): RgbImage {
        val side = 40
        return RgbImage(side, side, ByteArray(side * side * 3) { at ->
            val x = at / 3 % side; val y = at / 3 / side
            val digit = x in 18..21 && y in 12..27
            (if (digit) { if (whiteGlyph) 255 else 20 }
             else if (coloredPaper) intArrayOf(65, 180, 195)[at % 3] else 245).toByte()
        })
    }
    @Test fun refusesDenseWhiteLabelsOnColoredTiles() {
        assertTrue(containsResultOverlays(List(81) { if (it < 24) tile(true) else tile(false, false) }))
    }
    @Test fun recognizesLabelsCoveringOnlyPartOfEachCell() {
        val side = 40
        val cell = RgbImage(side, side, ByteArray(side * side * 3) { at ->
            val x = at / 3 % side; val y = at / 3 / side
            when {
                x in 23..27 && y in 12..27 -> 255
                x >= 20 -> intArrayOf(65, 180, 195)[at % 3]
                else -> 160
            }.toByte()
        })
        assertTrue(containsResultOverlays(List(81) { if (it < 24) cell else tile(false, false) }))
    }
    @Test fun permitsColoredPaperAndDarkInkAndSparseEditorialMarks() {
        assertFalse(containsResultOverlays(List(81) { tile(false) }))
        assertFalse(containsResultOverlays(List(81) { if (it < 8) tile(true) else tile(false, false) }))
    }
}
