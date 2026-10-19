@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package org.freevia.sudokubuddy.shared
import kotlinx.cinterop.*
import platform.Foundation.NSData
import platform.Foundation.create
import org.freevia.sudokubuddy.vision.*
import platform.posix.memcpy

data class IosRectifiedPixels(val rgb: NSData,val luminance: NSData)

class IosScanner {
    fun rectify(width: Int,height: Int,rgb: NSData,corners: String): IosRectifiedPixels {
        require(width>0 && height>0 && rgb.length.toLong()==width.toLong()*height*3)
        val points=corners.split(',').map { it.toDouble() }.chunked(2).map { Corner(it[0],it[1]) }
        require(points.size==4)
        val quad=Quad(points[0],points[1],points[2],points[3])
        val source=ByteArray(rgb.length.toInt()).also { bytes -> bytes.usePinned { memcpy(it.addressOf(0),rgb.bytes,rgb.length) } }
        val color=PerspectivePixels.rgb(RgbImage(width,height,source),quad,1152)
        val gray=PerspectivePixels.gray(PhotoLuma.image(width,height,source),quad,1152)
        fun data(bytes: ByteArray): NSData = bytes.usePinned { NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()) }
        return IosRectifiedPixels(data(color.pixels),data(gray.pixels))
    }

    fun rectifySurface(width: Int,height: Int,rgb: NSData,coefficients: String): IosRectifiedPixels {
        require(width>0 && height>0 && rgb.length.toLong()==width.toLong()*height*3)
        val source=ByteArray(rgb.length.toInt()).also { bytes -> bytes.usePinned { memcpy(it.addressOf(0),rgb.bytes,rgb.length) } }
        val gray=PerspectivePixels.surface(PhotoLuma.image(width,height,source),coefficients.split(',').map { it.toDouble() }.toDoubleArray(),1152)
        val color=ByteArray(gray.pixels.size*3){gray.pixels[it/3]}
        fun data(bytes:ByteArray):NSData=bytes.usePinned { NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()) }
        return IosRectifiedPixels(data(color),data(gray.pixels))
    }
    fun gridIsVisible(width: Int, height: Int, luminance: NSData): Boolean {
        if (width !in 450..1600 || height != width || luminance.length.toLong() != width.toLong() * height) return false
        val bytes = ByteArray(luminance.length.toInt())
        bytes.usePinned { memcpy(it.addressOf(0), luminance.bytes, luminance.length) }
        return org.freevia.sudokubuddy.vision.GridLineFitter.fit(
            org.freevia.sudokubuddy.vision.GrayImage(width, height, bytes), requireStrongLines = true) != null
    }
    fun read(width: Int, height: Int, rgb: NSData, verifiedGeometry: Boolean): ScanReading {
        if (rgb.length.toLong() != width.toLong() * height * 3 || width !in 450..1600 || height != width) {
            return ScanReading("unreadable", "Invalid grid image.")
        }
        val bytes = ByteArray(rgb.length.toInt())
        bytes.usePinned { memcpy(it.addressOf(0), rgb.bytes, rgb.length) }
        return ScanEngine().read(width, height, bytes, verifiedGeometry)
    }
    fun readRectified(width: Int, height: Int, rgb: NSData, luminance: NSData, colorAvailable: Boolean): ScanReading {
        if (width !in 450..1600 || width != height || rgb.length.toLong() != width.toLong()*height*3 || luminance.length.toLong() != width.toLong()*height)
            return ScanReading("unreadable", "Invalid grid image.")
        fun copy(data: NSData) = ByteArray(data.length.toInt()).also { bytes -> bytes.usePinned { memcpy(it.addressOf(0),data.bytes,data.length) } }
        return ScanEngine().read(width,height,copy(rgb),verifiedGeometry=true,luminance=copy(luminance),colorAvailable=colorAvailable)
    }

}

/** Lazily materialized only for the fallback that needs rule alignment. */
class IosRuleAligner(private val width:Int,private val height:Int,private val rgb:NSData) {
    private val gray:GrayImage by lazy {
        val pixels=ByteArray(rgb.length.toInt()).also { bytes->bytes.usePinned { memcpy(it.addressOf(0),rgb.bytes,rgb.length) } }
        PhotoLuma.image(width,height,pixels)
    }
    fun align(corners:String):String {
        val p=corners.split(',').map { it.toDouble() }.chunked(2).map { Corner(it[0],it[1]) }
        require(p.size==4)
        return RuleAlignment.align(gray,Quad(p[0],p[1],p[2],p[3])).corners.flatMap { listOf(it.x,it.y) }.joinToString(",")
    }
}
