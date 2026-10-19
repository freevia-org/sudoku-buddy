package org.freevia.sudokubuddy.recognize
import org.freevia.sudokubuddy.vision.GrayImage
import kotlin.test.*

class PortableRecognitionTest {
    @Test fun rectangularCellsKeepIndependentMasksAndMeasurements() {
        fun cell(dx: Int): GrayImage {
            val pixels = ByteArray(73 * 91) { 230.toByte() }
            for (y in 19..51) for (x in 22 + dx..28 + dx) pixels[y * 73 + x] = 60
            return GrayImage(73, 91, pixels)
        }
        val readings = CellAnalyzer.inspect(listOf(cell(0), GrayImage(73, 91, ByteArray(73 * 91) { 230.toByte() }), cell(7)))
        val first = assertNotNull(readings[0]); val moved = assertNotNull(readings[2])
        assertNull(readings[1])
        assertEquals(first.blob.left + 7, moved.blob.left)
        assertEquals(first.blob.area, moved.blob.area)
        first.normalised.indices.forEach { assertEquals(first.normalised[it], moved.normalised[it], 0.00001f) }
    }
    @Test fun malformedModelIsRejectedBeforeInference() {
        assertFailsWith<IllegalArgumentException> { DigitClassifier.read(byteArrayOf(1, 2, 3)) }
    }
}
