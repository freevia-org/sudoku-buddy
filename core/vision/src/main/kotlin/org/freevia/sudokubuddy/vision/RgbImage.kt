package org.freevia.sudokubuddy.vision


/** Uses exactly the grayscale gate's accepted transform and cell bounds. */
object ColorCells {
    fun extract(image: RgbImage, verdict: GateVerdict.Usable): List<RgbImage>? {
        if (verdict.curved) return null
        val width = verdict.rectified.width
        val height = verdict.rectified.height
        val pixels = PerspectivePixels.rgb(image,verdict.quad,width).pixels
        return (0 until 81).map { index ->
                val bounds = verdict.geometry.cellBounds(index)
                val left = bounds.left.coerceIn(0, width - 1)
                val top = bounds.top.coerceIn(0, height - 1)
                val right = bounds.right.coerceIn(left + 1, width)
                val bottom = bounds.bottom.coerceIn(top + 1, height)
                val cellWidth = right - left
                val cellHeight = bottom - top
                val cell = ByteArray(cellWidth * cellHeight * 3)
                for (y in 0 until cellHeight) {
                    System.arraycopy(pixels, ((top + y) * width + left) * 3,
                        cell, y * cellWidth * 3, cellWidth * 3)
                }
                RgbImage(cellWidth, cellHeight, cell)
            }
    }
}
