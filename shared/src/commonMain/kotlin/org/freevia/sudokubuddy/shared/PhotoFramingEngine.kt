package org.freevia.sudokubuddy.shared

import org.freevia.sudokubuddy.vision.*

class PhotoFramingEngine {
    fun note(width: Int, height: Int, corners: String): String {
        val values = corners.split(',').mapNotNull { it.toDoubleOrNull()?.takeIf { number -> number.isFinite() } }
        if (values.size != 8 || width <= 0 || height <= 0) return ""
        val p = values.chunked(2).map { Corner(it[0], it[1]) }
        return PhotoFraming.note(Quad(p[0], p[1], p[2], p[3]), width, height).orEmpty()
    }
}
