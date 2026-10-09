package org.freevia.sudokubuddy.vision

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Interleaved RGB bytes; color is retained only for captured-image recognition. */
data class RgbImage(val width: Int, val height: Int, val pixels: ByteArray) {
    init {
        require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height * 3)
    }
}

/** Uses exactly the grayscale gate's accepted transform and cell bounds. */
object ColorCells {
    fun extract(image: RgbImage, verdict: GateVerdict.Usable): List<RgbImage>? {
        if (verdict.curved) return null
        val source = Mat(image.height, image.width, CvType.CV_8UC3)
        val corners = MatOfPoint2f(*verdict.quad.corners.map { Point(it.x, it.y) }.toTypedArray())
        val width = verdict.rectified.width
        val height = verdict.rectified.height
        val target = MatOfPoint2f(Point(0.0, 0.0), Point(width.toDouble(), 0.0),
            Point(width.toDouble(), height.toDouble()), Point(0.0, height.toDouble()))
        val transform = Imgproc.getPerspectiveTransform(corners, target)
        val rectified = Mat()
        try {
            source.put(0, 0, image.pixels)
            Imgproc.warpPerspective(source, rectified, transform, Size(width.toDouble(), height.toDouble()))
            val pixels = ByteArray(width * height * 3)
            rectified.get(0, 0, pixels)
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
        } finally {
            source.release()
            corners.release()
            target.release()
            transform.release()
            rectified.release()
        }
    }
}
