package org.freevia.sudokubuddy.recognize
import org.freevia.sudokubuddy.vision.GrayImage
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

    internal fun openCvLabelCellInk(cell: GrayImage): InkMask {
        val gray = Mat(cell.height, cell.width, CvType.CV_8UC1)
        gray.put(0, 0, cell.pixels)
        val grayF = Mat()
        val local = Mat()
        val threshold = Mat()
        val mask = Mat()
        // Remove single-pixel speckle from paper texture before labelling.
        val opened = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
        val centroids = Mat()
        val labels = Mat()
        val stats = Mat()
        try {
            gray.convertTo(grayF, CvType.CV_32F)
            Imgproc.blur(grayF, local, Size(CellAnalyzer.LOCAL_WINDOW.toDouble(), CellAnalyzer.LOCAL_WINDOW.toDouble()),
                org.opencv.core.Point(-1.0, -1.0), Core.BORDER_REFLECT)
            Core.subtract(local, org.opencv.core.Scalar(CellAnalyzer.INK_MARGIN), threshold)
            Core.compare(grayF, threshold, mask, Core.CMP_LT)
            Imgproc.morphologyEx(mask, opened, Imgproc.MORPH_OPEN, kernel)
            val count = Imgproc.connectedComponentsWithStats(opened, labels, stats, centroids)
            return InkMask(
                IntArray(gray.rows() * gray.cols()).also { labels.get(0, 0, it) },
                IntArray(count * Imgproc.CC_STAT_MAX).also { stats.get(0, 0, it) },
                count,
            )
        } finally {
            labels.release()
            stats.release()
            gray.release()
            grayF.release()
            local.release()
            threshold.release()
            mask.release()
            opened.release()
            kernel.release()
            centroids.release()
        }
    }


internal fun openCvResizeCellGlyph(glyph: FloatArray, width: Int, height: Int, newWidth: Int, newHeight: Int): FloatArray {
    val buffer = FloatArray(newWidth * newHeight)

        val glyphMat = Mat(height, width, CvType.CV_32F)
        val small = Mat()
        try {
            glyphMat.put(0, 0, glyph)
            Imgproc.resize(
                glyphMat, small, Size(newWidth.toDouble(), newHeight.toDouble()),
                0.0, 0.0, Imgproc.INTER_AREA,
            )
            small.get(0, 0, buffer)
        } finally {
            glyphMat.release()
            small.release()
        }

    return buffer
}

@Suppress("UNUSED_PARAMETER")
internal fun CellAnalyzer.findBlobs(gray: Mat, cell: GrayImage): List<Blob> = findBlobs(cell)
