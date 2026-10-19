package org.freevia.sudokubuddy.shared

import org.freevia.sudokubuddy.recognize.GridReader
import org.freevia.sudokubuddy.recognize.ReadResult
import org.freevia.sudokubuddy.model.CellSource
import org.freevia.sudokubuddy.vision.*

/** Reads an upright, perspective-corrected RGB grid. All role/confidence rules are shared with Android. */
class ScanEngine {
    fun read(width: Int, height: Int, rgb: ByteArray, verifiedGeometry: Boolean = false, luminance: ByteArray? = null, colorAvailable: Boolean = true): ScanReading {
        if (width !in 450..1600 || height != width || rgb.size.toLong() != width.toLong() * height * 3) {
            return ScanReading("unreadable", "The grid image is too small or has invalid dimensions.")
        }
        if (luminance != null && luminance.size.toLong() != width.toLong() * height) return ScanReading("unreadable", "Invalid grid image.")
        val gray = luminance?.let { GrayImage(width,height,it) } ?: PhotoLuma.image(width,height,rgb)
        val geometry = GridLineFitter.fit(gray, requireStrongLines = !verifiedGeometry)
            ?: if (verifiedGeometry) CellGeometry.evenNinths(width) else null
            ?: return ScanReading("unreadable", "Could not find nine rows and columns. Photograph the whole grid on a flat page, in good light.")
        val cells = CellExtractor.extract(gray, geometry)
        val color = (0 until 81).map { index ->
            val bounds = geometry.cellBounds(index)
            val left = bounds.left.coerceIn(0, width - 1)
            val top = bounds.top.coerceIn(0, height - 1)
            val right = bounds.right.coerceIn(left + 1, width)
            val bottom = bounds.bottom.coerceIn(top + 1, height)
            val w = right - left; val h = bottom - top
            val bytes = ByteArray(w * h * 3)
            repeat(h) { y -> rgb.copyInto(bytes, y * w * 3, ((top + y) * width + left) * 3, ((top + y) * width + right) * 3) }
            RgbImage(w, h, bytes)
        }
        val result = GridReader().read(cells, if (colorAvailable) color else null)
        val grid = when (result) {
            is ReadResult.Accepted -> result.grid
            is ReadResult.NeedsConfirmation -> result.grid
            is ReadResult.Unreadable -> return ScanReading("unreadable", result.reason)
        }
        val reports = when (result) {
            is ReadResult.Accepted -> result.readings
            is ReadResult.NeedsConfirmation -> result.readings
            is ReadResult.Unreadable -> emptyList()
        }
        val uncertain = (result as? ReadResult.NeedsConfirmation)?.uncertainCells.orEmpty()
        val original = grid.cells.chunked(9).joinToString("\n") { row -> row.joinToString("") { it.digit?.toString() ?: "." } }
        val evidence = buildString {
            append("{\"originalGrid\":${jsonString(original)},\"uncertainCells\":[${uncertain.sorted().joinToString(",")}],")
            append("\"readings\":[")
            append(reports.joinToString(",") { report ->
                val probabilities = report.probabilities
                val ranked = probabilities?.let { values -> values.indices.sortedByDescending { values[it] } }
                val first = ranked?.firstOrNull()
                val second = ranked?.getOrNull(1)
                fun probability(at: Int?): String = at?.let { probabilities?.get(it)?.takeIf { p -> p.isFinite() }?.toString() } ?: "null"
                "{\"cell\":${report.index},\"ink\":${jsonString(report.ink.name)},\"digit\":${first?.plus(1) ?: "null"}," +
                    "\"confidence\":${probability(first)},\"runnerUp\":${second?.plus(1) ?: "null"}," +
                    "\"runnerUpConfidence\":${probability(second)},\"roleUncertain\":${report.roleUncertain}}"
            })
            append("],\"gridLines\":{\"vertical\":[${geometry.verticalLines.map { it / width }.joinToString(",")}],")
            append("\"horizontal\":[${geometry.horizontalLines.map { it / height }.joinToString(",")}]},\"corrections\":[]}")
        }
        return ScanReading(
            status = "review",
            message = when (result) {
                is ReadResult.NeedsConfirmation -> result.reason
                else -> "Review the printed clues and handwritten answers before starting."
            },
            givens = grid.cells.joinToString("") { if (it.source == CellSource.GIVEN) it.digit.toString() else "." },
            progress = grid.cells.joinToString("") { if (it.source == CellSource.GUESS) it.digit.toString() else "." },
            uncertainCells = uncertain.sorted().joinToString(","),
            evidenceJson = evidence,
            readerComplaint = (result as? ReadResult.NeedsConfirmation)?.reason.orEmpty(),
        )
    }
}

data class ScanReading(val status: String, val message: String, val givens: String = "", val progress: String = "", val uncertainCells: String = "", val evidenceJson: String = "", val readerComplaint: String = "")
