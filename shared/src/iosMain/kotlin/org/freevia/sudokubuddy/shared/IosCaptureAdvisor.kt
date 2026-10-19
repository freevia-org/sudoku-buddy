@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.freevia.sudokubuddy.shared

import kotlinx.cinterop.*
import platform.Foundation.NSData
import platform.posix.memcpy
import org.freevia.sudokubuddy.vision.*

/** Stateful, camera-queue-owned bridge to the exact Android capture policy. */
class IosCaptureAdvisor {
    private val policy = CapturePolicy()
    fun reset() = policy.reset()
    fun observe(width: Int, height: Int, luminance: NSData, corners: String,
                gridSide: Int, gridLuminance: NSData, candidates: Int, nearGrid: Boolean): String {
        fun gray(w: Int, h: Int, data: NSData): GrayImage {
            require(w > 0 && h > 0 && w <= 1600 && h <= 1600 && data.length.toLong() == w.toLong() * h)
            val bytes = ByteArray(data.length.toInt())
            bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
            return GrayImage(w, h, bytes)
        }
        val points = corners.split(',').mapNotNull { it.toDoubleOrNull() }
        val quad = if (points.size == 8 && points.all { it.isFinite() && it in 0.0..1.0 })
            Quad(Corner(points[0]*width, points[1]*height), Corner(points[2]*width, points[3]*height),
                 Corner(points[4]*width, points[5]*height), Corner(points[6]*width, points[7]*height)) else null
        val advice = if (quad != null && gridSide > 0)
            policy.adviseLocated(width, height, quad, CapturePhotometry.of(gray(gridSide, gridSide, gridLuminance)))
        else policy.missed(width, height, CapturePhotometry.of(gray(width, height, luminance)), candidates, nearGrid,
            quad?.corners?.map { Corner(it.x/width, it.y/height) })
        return "{\"message\":" + jsonString(advice.message) + ",\"ready\":" + advice.readyToCapture +
            ",\"accepted\":" + advice.outlineAccepted + "}"
    }
}
