package org.freevia.sudokubuddy.recognize

import org.freevia.sudokubuddy.vision.GrayImage
import kotlin.math.ceil
import kotlin.math.floor

internal class InkMask(val labels: IntArray, val stats: IntArray, val count: Int)
internal fun labelCellInk(cell: GrayImage): InkMask = PortableCellOps.label(cell)
internal fun resizeCellGlyph(glyph: FloatArray, width: Int, height: Int, newWidth: Int, newHeight: Int): FloatArray =
    PortableCellOps.resize(glyph,width,height,newWidth,newHeight)

/** Native equivalents of OpenCV's reflected box mean, 2x2 opening and 8-connected labeling. */
internal object PortableCellOps {
    fun label(cell: GrayImage): InkMask {
        val w = cell.width
        val h = cell.height
        val window = CellAnalyzer.LOCAL_WINDOW
        val pad = window / 2
        val pw = w + 2 * pad
        val ph = h + 2 * pad
        fun reflected(value: Int, size: Int): Int {
            var at = value
            while (at < 0 || at >= size) at = if (at < 0) -at - 1 else 2 * size - at - 1
            return at
        }
        val integral = LongArray((pw + 1) * (ph + 1))
        for (y in 0 until ph) {
            var row = 0L
            for (x in 0 until pw) {
                row += cell[reflected(x - pad, w), reflected(y - pad, h)]
                integral[(y + 1) * (pw + 1) + x + 1] = integral[y * (pw + 1) + x + 1] + row
            }
        }
        val mask = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            fun sum(xx: Int, yy: Int) = integral[yy * (pw + 1) + xx]
            val mean = ((sum(x + window, y + window) - sum(x, y + window) - sum(x + window, y) + sum(x, y)).toDouble() / (window * window)).toFloat()
            mask[y * w + x] = cell[x, y].toFloat() < mean - CellAnalyzer.INK_MARGIN.toFloat()
        }
        val eroded = BooleanArray(w * h)
        val opened = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var yes = true
            for (dy in -1..0) for (dx in -1..0) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h && !mask[yy * w + xx]) yes = false
            }
            eroded[y * w + x] = yes
        }
        for (y in 0 until h) for (x in 0 until w) {
            var yes = false
            for (dy in -1..0) for (dx in -1..0) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h && eroded[yy * w + xx]) yes = true
            }
            opened[y * w + x] = yes
        }
        val labels = IntArray(w * h)
        val queue = IntArray(w * h)
        val stats = mutableListOf(0, 0, 0, 0, 0)
        var count = 1
        for (start in labels.indices) {
            if (!opened[start] || labels[start] != 0) continue
            val label = count++
            var head = 0; var tail = 1
            queue[0] = start; labels[start] = label
            var left = w; var top = h; var right = 0; var bottom = 0
            while (head < tail) {
                val at = queue[head++]
                val x = at % w; val y = at / w
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x); bottom = maxOf(bottom, y)
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx !in 0 until w || yy !in 0 until h) continue
                    val next = yy * w + xx
                    if (opened[next] && labels[next] == 0) {
                        labels[next] = label; queue[tail++] = next
                    }
                }
            }
            stats.addAll(listOf(left, top, right - left + 1, bottom - top + 1, tail))
        }
        return InkMask(labels, stats.toIntArray(), count)
    }

    /** Pixel-area averaging, preserving thin strokes when shrinking a glyph. */
    fun resize(glyph: FloatArray, width: Int, height: Int, outWidth: Int, outHeight: Int): FloatArray {
        val sx = width.toDouble() / outWidth
        val sy = height.toDouble() / outHeight
        return FloatArray(outWidth * outHeight) { at ->
            val left = (at % outWidth) * sx; val top = (at / outWidth) * sy
            val right = left + sx; val bottom = top + sy
            var sum = 0.0
            for (y in floor(top).toInt() until ceil(bottom).toInt().coerceAtMost(height)) {
                for (x in floor(left).toInt() until ceil(right).toInt().coerceAtMost(width)) {
                    val area = (minOf(right, x + 1.0) - maxOf(left, x.toDouble())) *
                        (minOf(bottom, y + 1.0) - maxOf(top, y.toDouble()))
                    sum += glyph[y * width + x] * area
                }
            }
            (sum / (sx * sy)).toFloat()
        }
    }
}
