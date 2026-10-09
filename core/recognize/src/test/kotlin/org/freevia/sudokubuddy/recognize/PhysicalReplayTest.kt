package org.freevia.sudokubuddy.recognize

import org.freevia.sudokubuddy.model.CellSource
import org.freevia.sudokubuddy.vision.CellExtractor
import org.freevia.sudokubuddy.vision.CellGeometry
import org.freevia.sudokubuddy.vision.GridLineFitter
import org.freevia.sudokubuddy.vision.OpenCvNatives
import org.freevia.sudokubuddy.vision.toGrayImage
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/** Diagnostic replay of saved, recompressed physical squares, not original inference pixels. */
class PhysicalReplayTest {
    @Test
    fun `report both physical squares and their component context`() {
        val directory = File(System.getProperty("probe", ""))
        if (!directory.isDirectory) return
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val reader = GridReader()
        val output = File("build/physical-replay.tsv")
        var images = 0
        output.bufferedWriter().use { out ->
            out.appendLine("image\tgeometry\tcell\trole\tdigit\tuncertain\treader\theight\tcontrast\tcompany\tcomponents")
            for (name in listOf("auto-square.jpg", "manual-square.jpg")) {
                val file = File(directory, name)
                if (!file.isFile) continue
                images++
                val image = ImageIO.read(file).toGrayImage()
                val fitted = GridLineFitter.fit(image)
                for ((mode, geometry) in listOf("fitted" to (fitted ?: CellGeometry.evenNinths(image.width)),
                    "even" to CellGeometry.evenNinths(image.width))) {
                    val cells = CellExtractor.extract(image, geometry)
                    val result = reader.read(cells)
                    val grid = when (result) {
                        is ReadResult.Accepted -> result.grid
                        is ReadResult.NeedsConfirmation -> result.grid
                        is ReadResult.Unreadable -> null
                    }
                    val uncertain = (result as? ReadResult.NeedsConfirmation)?.uncertainCells ?: emptySet()
                    val ink = CellAnalyzer.inspect(cells)
                    for (index in 0 until 81) {
                        val cell = cells[index]
                        val mat = Mat(cell.height, cell.width, CvType.CV_8UC1)
                        val blobs = try {
                            mat.put(0, 0, cell.pixels)
                            CellAnalyzer.findBlobs(mat, cell)
                        } finally { mat.release() }
                        val role = when (grid?.get(index)?.source) {
                            CellSource.GIVEN -> "GIVEN"
                            CellSource.GUESS -> "GUESS"
                            else -> "EMPTY"
                        }
                        val components = blobs.sortedByDescending { it.area }.joinToString(";") {
                            "${it.left},${it.top},${it.width},${it.height},${it.area},${"%.1f".format(it.contrast)}"
                        }
                        out.appendLine(listOf(name, mode, index, role, grid?.get(index)?.digit ?: "",
                            index in uncertain, result.javaClass.simpleName, ink[index]?.blob?.heightRatio ?: "",
                            ink[index]?.blob?.contrast ?: "", ink[index]?.company ?: "", components).joinToString("\t"))
                    }
                }
            }
        }
        assertEquals(2, images, "the diagnostic requires both saved physical squares")
    }
}
