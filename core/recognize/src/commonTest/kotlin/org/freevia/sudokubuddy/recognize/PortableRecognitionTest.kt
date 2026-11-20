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
    @Test fun countedMedianMatchesTheSortedOneForEveryStride() {
        val random = kotlin.random.Random(7)
        for (size in listOf(1, 2, 3, 6, 9, 300, 303)) {
            val pixels = ByteArray(size) { random.nextInt(256).toByte() }
            assertEquals(pixels.map { it.toInt() and 0xFF }.sorted()[size / 2], medianLevel(pixels))
            if (size % 3 == 0) for (channel in 0..2) {
                val samples = (0 until size / 3).map { pixels[it * 3 + channel].toInt() and 0xFF }.sorted()
                assertEquals(samples[samples.size / 2], medianLevel(pixels, channel, 3))
            }
        }
    }
    @Test fun manyComponentsKeepTheirOwnStatistics() {
        val w = 90; val h = 90
        val pixels = ByteArray(w * h) { 230.toByte() }
        var drawn = 0
        for (top in 4 until h - 6 step 8) for (left in 4 until w - 6 step 8) {
            for (y in top until top + 4) for (x in left until left + 4) pixels[y * w + x] = 40
            drawn++
        }
        val mask = PortableCellOps.label(GrayImage(w, h, pixels))
        assertEquals(drawn + 1, mask.count)
        assertEquals(mask.count * 5, mask.stats.size)
        for (label in 1 until mask.count) {
            assertEquals(mask.labels.count { it == label }, mask.stats[label * 5 + 4])
            assertEquals(mask.labels.indexOfFirst { it == label } / w, mask.stats[label * 5 + 1])
        }
    }
    @Test fun malformedModelIsRejectedBeforeInference() {
        assertFailsWith<IllegalArgumentException> { DigitClassifier.read(byteArrayOf(1, 2, 3)) }
    }
}
