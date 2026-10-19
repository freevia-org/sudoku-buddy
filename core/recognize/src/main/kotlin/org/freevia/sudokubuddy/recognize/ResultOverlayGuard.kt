package org.freevia.sudokubuddy.recognize

import org.freevia.sudokubuddy.vision.RgbImage

/** Result screenshots replace photographed ink with white labels on filled colored tiles.
 * Reading those labels/underlying ink as a fresh paper puzzle invents clues. Ordinary
 * colored pen and highlighted paper retain dark glyphs and do not meet this condition.
 */
internal fun containsResultOverlays(cells: List<RgbImage>): Boolean {
    var overlaid = 0
    for (cell in cells) {
        var colored = 0; var white = 0; var count = 0
        val left = cell.width / 5; val right = cell.width * 4 / 5
        val top = cell.height / 5; val bottom = cell.height * 4 / 5
        for (y in top until bottom step 2) for (x in left until right step 2) {
            val at = (y * cell.width + x) * 3
            val r = cell.pixels[at].toInt() and 255
            val g = cell.pixels[at + 1].toInt() and 255
            val b = cell.pixels[at + 2].toInt() and 255
            val high = maxOf(r, g, b); val low = minOf(r, g, b)
            if (high - low > 55 && high > 90) colored++
            if (low >= 230 && high - low < 16) white++
            count++
        }
        if (count > 0 && colored > count * 0.30 && white > count * 0.01 && white < count * 0.25) overlaid++
    }
    return overlaid >= 16
}
