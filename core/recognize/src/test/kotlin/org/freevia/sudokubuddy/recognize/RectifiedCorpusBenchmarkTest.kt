package org.freevia.sudokubuddy.recognize

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import org.freevia.sudokubuddy.vision.CellExtractor
import org.freevia.sudokubuddy.vision.CellGeometry
import org.freevia.sudokubuddy.vision.GrayImage
import org.freevia.sudokubuddy.vision.GridLineFitter
import org.freevia.sudokubuddy.vision.RgbImage
import org.freevia.sudokubuddy.vision.OpenCvNatives

/** Additional already-rectified fixtures: keep their recognition denominator separate. */
class RectifiedCorpusBenchmarkTest {
    @Test fun exportRectifiedBenchmark() {
        if (System.getProperty("dump") != "true") return
        val home = File("../../corpus-rectified")
        check(home.isDirectory) { "Rectified corpus missing" }
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val output = File("build/rectified-benchmark.tsv")
        output.parentFile.mkdirs()
        output.bufferedWriter().use { out ->
            out.appendLine("photo\tcell\texpectedRole\texpectedDigit\tactualRole\tactualDigit")
            for (file in home.listFiles().orEmpty().filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png") }.sortedBy { it.name }) {
                val truth = checkNotNull(CorpusLabels.forPhoto(file.name))
                val image = checkNotNull(ImageIO.read(file)); val w = image.width; val h = image.height
                val rgb = ByteArray(w * h * 3); val gray = ByteArray(w * h)
                for (y in 0 until h) for (x in 0 until w) {
                    val value = image.getRGB(x, y); val i = y * w + x
                    val r = value shr 16 and 255; val g = value shr 8 and 255; val b = value and 255
                    rgb[i*3] = r.toByte(); rgb[i*3+1] = g.toByte(); rgb[i*3+2] = b.toByte()
                    gray[i] = ((r*299 + g*587 + b*114) / 1000).toByte()
                }
                val source = GrayImage(w, h, gray)
                val geometry = GridLineFitter.fit(source) ?: CellGeometry.evenNinths(w)
                val colors = (0 until 81).map { index ->
                    val bounds = geometry.cellBounds(index)
                    val left = bounds.left.coerceIn(0, w - 1); val right = bounds.right.coerceIn(left + 1, w)
                    val top = bounds.top.coerceIn(0, h - 1); val bottom = bounds.bottom.coerceIn(top + 1, h)
                    val cw = right-left; val ch = bottom-top; val data = ByteArray(cw*ch*3)
                    repeat(ch) { y -> rgb.copyInto(data, y*cw*3, ((top+y)*w+left)*3, ((top+y)*w+right)*3) }
                    RgbImage(cw, ch, data)
                }
                val result = GridReader().read(CellExtractor.extract(source, geometry), colors)
                val grid = when(result) {
                    is ReadResult.Accepted -> result.grid
                    is ReadResult.NeedsConfirmation -> result.grid
                    is ReadResult.Unreadable -> null
                }
                for (i in 0 until 81) out.appendLine(listOf(file.name, i, truth[i].source, truth[i].digit ?: 0,
                    grid?.get(i)?.source ?: "REFUSED", grid?.get(i)?.digit ?: 0).joinToString("\t"))
            }
        }
    }
}
