package org.freevia.sudokubuddy.vision

import kotlin.test.*

class PerspectivePixelsTest {
    private fun square(x:Double,y:Double,size:Double)=Quad(Corner(x,y),Corner(x+size,y),Corner(x+size,y+size),Corner(x,y+size))
    @Test fun identityPreservesEveryGrayAndColorByte() {
        val pixels=ByteArray(32*32){it.toByte()}
        assertContentEquals(pixels,PerspectivePixels.gray(GrayImage(32,32,pixels),square(0.0,0.0,32.0),32).pixels)
        val color=ByteArray(32*32*3){(it*17).toByte()}
        assertContentEquals(color,PerspectivePixels.rgb(RgbImage(32,32,color),square(0.0,0.0,32.0),32).pixels)
    }
    @Test fun fractionalSamplingRoundsOnceAndKeepsChannelOrder() {
        val color=RgbImage(8,8,ByteArray(8*8*3){at -> when(at%3){0->at/3%8*8;1->at/3/8*16;else->127}.toByte()})
        val out=PerspectivePixels.rgb(color,square(0.5,0.5,4.0),4)
        assertEquals(listOf(4,8,127),out.pixels.take(3).map { it.toInt() and 255 })
        assertEquals(listOf(28,56,127),out.pixels.takeLast(3).map { it.toInt() and 255 })
    }
    @Test fun quarticSurfaceUsesAndroidCoefficientOrderAndReplicatedEdges() {
        val image=GrayImage(9,9,ByteArray(81){it.toByte()})
        val coefficients=DoubleArray(30);coefficients[0]=0.5;coefficients[1]=0.5;coefficients[3]=1.0;coefficients[4]=1.0
        assertContentEquals(image.pixels,PerspectivePixels.surface(image,coefficients,9).pixels)
        val edge=PerspectivePixels.remap(image,floatArrayOf(-2f,10f,-1f,9f),floatArrayOf(-2f,-1f,10f,9f),2)
        assertContentEquals(byteArrayOf(0,8,72,80),edge.pixels)
    }
    @Test fun perspectiveUsesHomogeneousCoordinatesAndBlackOutsidePhoto() {
        val image=GrayImage(10,10,ByteArray(100){(it/10*10+it%10).toByte()})
        val trapezoid=Quad(Corner(0.0,0.0),Corner(8.0,0.0),Corner(6.0,8.0),Corner(2.0,8.0))
        assertEquals(57,PerspectivePixels.gray(image,trapezoid,2)[1,1])
        assertEquals(0,PerspectivePixels.gray(image,square(-2.0,-2.0,4.0),4)[0,0])
        assertFailsWith<IllegalArgumentException> { PerspectivePixels.gray(image,square(1.0,1.0,0.0),4) }
    }
}
