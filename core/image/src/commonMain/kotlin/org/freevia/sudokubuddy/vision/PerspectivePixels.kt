package org.freevia.sudokubuddy.vision

import kotlin.math.round

/** Deterministic final photo warp. Geometry scoring can stay native; recognition pixels
 * use one 1/32-pixel bilinear sampler on both platforms, with black outside the source. */
object PerspectivePixels {
    fun gray(image: GrayImage, quad: Quad, side: Int, extent: Double = side.toDouble()): GrayImage =
        GrayImage(side, side, warp(image.pixels,image.width,image.height,1,quad,side,extent))
    fun rgb(image: RgbImage, quad: Quad, side: Int): RgbImage =
        RgbImage(side, side, warp(image.pixels,image.width,image.height,3,quad,side))

    private fun warp(pixels: ByteArray, width: Int, height: Int, channels: Int, quad: Quad, side: Int, extent: Double = side.toDouble()): ByteArray {
        require(side > 0)
        val q=quad.corners
        val dx1=q[1].x-q[2].x; val dx2=q[3].x-q[2].x
        val dy1=q[1].y-q[2].y; val dy2=q[3].y-q[2].y
        val dx3=q[0].x-q[1].x+q[2].x-q[3].x; val dy3=q[0].y-q[1].y+q[2].y-q[3].y
        val determinant=dx1*dy2-dx2*dy1
        require(determinant.isFinite() && determinant != 0.0) { "Degenerate photograph quadrilateral" }
        val g=(dx3*dy2-dx2*dy3)/determinant; val h=(dx1*dy3-dx3*dy1)/determinant
        val a=q[1].x-q[0].x+g*q[1].x; val b=q[3].x-q[0].x+h*q[3].x
        val d=q[1].y-q[0].y+g*q[1].y; val e=q[3].y-q[0].y+h*q[3].y
        val result=ByteArray(side*side*channels)
        for(y in 0 until side) for(x in 0 until side) {
            val s=x.toDouble()/extent; val t=y.toDouble()/extent; val denominator=g*s+h*t+1
            val xx=(a*s+b*t+q[0].x)/denominator; val yy=(d*s+e*t+q[0].y)/denominator
            sample(pixels,width,height,channels,xx,yy,result,(y*side+x)*channels,false)
        }
        return result
    }

    fun point(quad: Quad,x:Double,y:Double,extent:Double): Corner {
        val q=quad.corners
        val dx1=q[1].x-q[2].x;val dx2=q[3].x-q[2].x;val dy1=q[1].y-q[2].y;val dy2=q[3].y-q[2].y
        val dx3=q[0].x-q[1].x+q[2].x-q[3].x;val dy3=q[0].y-q[1].y+q[2].y-q[3].y
        val determinant=dx1*dy2-dx2*dy1
        val g=(dx3*dy2-dx2*dy3)/determinant;val h=(dx1*dy3-dx3*dy1)/determinant
        val s=x/extent;val t=y/extent;val denominator=g*s+h*t+1
        return Corner(((q[1].x-q[0].x+g*q[1].x)*s+(q[3].x-q[0].x+h*q[3].x)*t+q[0].x)/denominator,
            ((q[1].y-q[0].y+g*q[1].y)*s+(q[3].y-q[0].y+h*q[3].y)*t+q[0].y)/denominator)
    }

    /** Same final sampler for a curved-page map; map coordinates are native 32-bit floats. */
    fun remap(image: GrayImage, xs: FloatArray, ys: FloatArray, side: Int): GrayImage {
        require(xs.size == side*side && ys.size == xs.size)
        val result=ByteArray(xs.size)
        for(at in xs.indices) sample(image.pixels,image.width,image.height,1,xs[at].toDouble(),ys[at].toDouble(),result,at,true)
        return GrayImage(side,side,result)
    }

    /** Quartic surface coefficients are interleaved x/y, in increasing total degree. */
    fun surface(image: GrayImage, coefficients: DoubleArray, side: Int): GrayImage {
        require(coefficients.size==30 && coefficients.all { it.isFinite() })
        val xs=FloatArray(side*side);val ys=FloatArray(xs.size)
        fun power(value:Double,exponent:Int):Double { var result=1.0;repeat(exponent){result*=value};return result }
        for(row in 0 until side) for(column in 0 until side) {
            val x=column.toDouble()/side*9.0-0.5;val y=row.toDouble()/side*9.0-0.5
            var xx=0.0;var yy=0.0;var t=0
            for(total in 0..4) for(i in 0..total){val term=power(x,i)*power(y,total-i);xx+=term*coefficients[t*2];yy+=term*coefficients[t*2+1];t++}
            val at=row*side+column;xs[at]=xx.toFloat();ys[at]=yy.toFloat()
        }
        return remap(image,xs,ys,side)
    }

    private fun sample(source: ByteArray,w:Int,h:Int,channels:Int,x:Double,y:Double,out:ByteArray,at:Int,replicate:Boolean) {
        if (!x.isFinite() || !y.isFinite()) return
        val xx=if(replicate)x.coerceIn(0.0,w-1.0) else x
        val yy=if(replicate)y.coerceIn(0.0,h-1.0) else y
        if(xx < -1 || yy < -1 || xx > w || yy > h) return
        val fixedX=round(xx*32).toInt();val fixedY=round(yy*32).toInt()
        val left=fixedX shr 5;val top=fixedY shr 5;val fx=fixedX and 31;val fy=fixedY and 31
        for(c in 0 until channels) {
            var sum=0
            for(dy in 0..1) for(dx in 0..1) {
                var sx=left+dx;var sy=top+dy
                if(replicate){sx=sx.coerceIn(0,w-1);sy=sy.coerceIn(0,h-1)}
                if(sx in 0 until w && sy in 0 until h)
                    sum+=(source[(sy*w+sx)*channels+c].toInt() and 255)*(if(dx==0)32-fx else fx)*(if(dy==0)32-fy else fy)
            }
            out[at+c]=((sum+512)/1024).toByte()
        }
    }
}
