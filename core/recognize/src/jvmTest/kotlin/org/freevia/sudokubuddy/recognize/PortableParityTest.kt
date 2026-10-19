package org.freevia.sudokubuddy.recognize
import org.freevia.sudokubuddy.vision.GrayImage
import kotlin.random.Random
import kotlin.test.*

class PortableParityTest {
    @Test fun nativeMaskAndComponentsMatchOpenCV() {
        nu.pattern.OpenCV.loadLocally()
        val random = Random(42)
        repeat(8) { trial ->
            val w = 73 + trial; val h = 91 - trial
            val pixels = ByteArray(w * h) { (210 + random.nextInt(30)).toByte() }
            for (y in 16..55) for (x in 22..29) pixels[y * w + x] = 35
            for (y in 40..52) for (x in 39..49) pixels[y * w + x] = 70
            val image = GrayImage(w, h, pixels)
            val cv = openCvLabelCellInk(image)
            val native = PortableCellOps.label(image)
            assertEquals(cv.count, native.count)
            assertEquals(cv.labels.map { it != 0 }, native.labels.map { it != 0 })
            fun components(mask: InkMask) = (1 until mask.count).map { label -> mask.stats.slice(label * 5 until label * 5 + 5) }.sortedBy { it.toString() }
            assertEquals(components(cv), components(native))
        }
    }
    @Test fun nativeAreaResizeMatchesOpenCVForClassifierReductions() {
        val random = Random(17)
        nu.pattern.OpenCV.loadLocally()
        for ((w, h) in listOf(35 to 67, 28 to 28, 17 to 43, 12 to 12, 7 to 19)) {
            val input = FloatArray(w * h) { random.nextFloat() }
            val nw = maxOf(1, (w * 20.0 / maxOf(w, h)).toInt())
            val nh = maxOf(1, (h * 20.0 / maxOf(w, h)).toInt())
            val cv = openCvResizeCellGlyph(input, w, h, nw, nh)
            val native = PortableCellOps.resize(input, w, h, nw, nh)
            cv.indices.forEach { assertEquals(cv[it], native[it], 0.00001f, "${w}x$h at $it") }
        }
    }
}
