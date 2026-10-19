package org.freevia.sudokubuddy.recognize

import java.io.File
import org.freevia.sudokubuddy.vision.*
import kotlin.test.Test

/** Offline comparison from the exact same decoded RGB bytes, without JPEG decoder differences. */
class NativeGeometryComparisonTest {
    @Test fun compareGeometryAndReading() {
        val root = System.getProperty("probe")?.takeIf { it.isNotEmpty() }?.let(::File) ?: return
        val dimensions = File(root, "source.size").readText().trim().split(Regex("\\s+")).map(String::toInt)
        val (width, height) = dimensions
        val rgb = File(root, "source.rgb").readBytes()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val verdict = StructuralGate.assess(PhotoLuma.image(width, height, rgb))
        check(verdict is GateVerdict.Usable) { "Android geometry refused: $verdict" }
        File(root, "jvm.gray").writeBytes(verdict.rectified.pixels)
        val nativeGray = GrayImage(1152, 1152, File(root, "native.gray").readBytes())
        val nativeGeometry = GridLineFitter.fit(nativeGray) ?: CellGeometry.evenNinths(1152)
        val differences = CellExtractor.extract(nativeGray, nativeGeometry).mapIndexedNotNull { index, cell ->
            val cv = openCvLabelCellInk(cell); val portable = PortableCellOps.label(cell)
            fun components(mask: InkMask) = (1 until mask.count).map { mask.stats.slice(it*5 until it*5+5) }.sortedBy { it.toString() }
            if (components(cv) != components(portable) || cv.labels.indices.any { (cv.labels[it] != 0) != (portable.labels[it] != 0) }) index else null
        }
        File(root, "cell-mask-differences.txt").writeText(differences.toString())
        File(root,"ink.txt").writeText(CellAnalyzer.inspect(verdict.cells).mapIndexed { index, ink -> "$index: ${ink?.blob} pieces=${ink?.pieces}" }.joinToString("\n"))
        val reading = GridReader().read(verdict.cells, ColorCells.extract(RgbImage(width, height, rgb), verdict))
        val grid = when (reading) {
            is ReadResult.Accepted -> reading.grid
            is ReadResult.NeedsConfirmation -> reading.grid
            is ReadResult.Unreadable -> error(reading.reason)
        }
        File(root, "jvm.txt").writeText("quad=${verdict.quad}\ncurved=${verdict.curved}\nscore=${verdict.gridScore}\nsize=${verdict.rectified.width}\ngrid=$grid\nreading=$reading\n")
    }
}
