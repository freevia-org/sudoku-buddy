@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package org.freevia.sudokubuddy.shared
import kotlinx.cinterop.*
import platform.Foundation.NSData
import platform.Foundation.create
import org.freevia.sudokubuddy.vision.*
import platform.posix.memcpy

data class IosRectifiedPixels(val rgb: NSData,val luminance: NSData)

internal fun NSData.toByteArray(): ByteArray {
    require(length <= Int.MAX_VALUE.toULong())
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

private fun nsData(bytes: ByteArray): NSData =
    if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()) }

// Swift cannot catch a Kotlin exception: one that leaves these classes ends the process.
// Each entry point answers with a value its caller already treats as "no result".
class IosScanner {
    fun rectify(width: Int,height: Int,rgb: NSData,corners: String): IosRectifiedPixels = runCatching {
        require(width>0 && height>0 && rgb.length.toLong()==width.toLong()*height*3)
        val points=corners.split(',').map { it.toDouble() }.chunked(2).map { Corner(it[0],it[1]) }
        require(points.size==4)
        val quad=Quad(points[0],points[1],points[2],points[3])
        val source=rgb.toByteArray()
        val color=PerspectivePixels.rgb(RgbImage(width,height,source),quad,1152)
        val gray=PerspectivePixels.gray(PhotoLuma.image(width,height,source),quad,1152)
        IosRectifiedPixels(nsData(color.pixels),nsData(gray.pixels))
    }.getOrElse { IosRectifiedPixels(NSData(),NSData()) }

    fun rectifySurface(width: Int,height: Int,rgb: NSData,coefficients: String): IosRectifiedPixels = runCatching {
        require(width>0 && height>0 && rgb.length.toLong()==width.toLong()*height*3)
        val source=rgb.toByteArray()
        val gray=PerspectivePixels.surface(PhotoLuma.image(width,height,source),coefficients.split(',').map { it.toDouble() }.toDoubleArray(),1152)
        val color=ByteArray(gray.pixels.size*3){gray.pixels[it/3]}
        IosRectifiedPixels(nsData(color),nsData(gray.pixels))
    }.getOrElse { IosRectifiedPixels(NSData(),NSData()) }
    fun gridIsVisible(width: Int, height: Int, luminance: NSData): Boolean {
        if (width !in 450..1600 || height != width || luminance.length.toLong() != width.toLong() * height) return false
        return runCatching {
            GridLineFitter.fit(GrayImage(width, height, luminance.toByteArray()), requireStrongLines = true) != null
        }.getOrDefault(false)
    }
    fun read(width: Int, height: Int, rgb: NSData, verifiedGeometry: Boolean): ScanReading {
        if (rgb.length.toLong() != width.toLong() * height * 3 || width !in 450..1600 || height != width) {
            return ScanReading("unreadable", "Invalid grid image.")
        }
        return runCatching { ScanEngine().read(width, height, rgb.toByteArray(), verifiedGeometry) }
            .getOrElse { ScanReading("unreadable", "Invalid grid image.") }
    }
    fun readRectified(width: Int, height: Int, rgb: NSData, luminance: NSData, colorAvailable: Boolean): ScanReading {
        if (width !in 450..1600 || width != height || rgb.length.toLong() != width.toLong()*height*3 || luminance.length.toLong() != width.toLong()*height)
            return ScanReading("unreadable", "Invalid grid image.")
        return runCatching { ScanEngine().read(width,height,rgb.toByteArray(),verifiedGeometry=true,luminance=luminance.toByteArray(),colorAvailable=colorAvailable) }
            .getOrElse { ScanReading("unreadable", "Invalid grid image.") }
    }

}

/** Lazily materialized only for the fallback that needs rule alignment. */
class IosRuleAligner(private val width:Int,private val height:Int,private val rgb:NSData) {
    private val gray:GrayImage by lazy { PhotoLuma.image(width,height,rgb.toByteArray()) }
    /** The corners it was given, when they cannot be aligned. */
    fun align(corners:String):String = runCatching {
        val p=corners.split(',').map { it.toDouble() }.chunked(2).map { Corner(it[0],it[1]) }
        require(p.size==4)
        RuleAlignment.align(gray,Quad(p[0],p[1],p[2],p[3])).corners.flatMap { listOf(it.x,it.y) }.joinToString(",")
    }.getOrDefault(corners)
}
