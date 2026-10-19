package org.freevia.sudokubuddy.vision
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
internal fun openCvGridInkProfiles(image: GrayImage): Pair<DoubleArray, DoubleArray> {
    val source = Mat(image.height, image.width, CvType.CV_8UC1)
    val binary = Mat()
    try {
        source.put(0, 0, image.pixels)
        Imgproc.adaptiveThreshold(source, binary, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 31, 10.0)
        val bytes = ByteArray(image.width * image.height)
        binary.get(0, 0, bytes)
        val columns = DoubleArray(image.width); val rows = DoubleArray(image.height)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (bytes[y * image.width + x].toInt() != 0) { columns[x]++; rows[y]++ }
        }
        return columns to rows
    } finally { source.release(); binary.release() }
}
