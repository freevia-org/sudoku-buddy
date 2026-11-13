package org.freevia.sudokubuddy.vision

import kotlin.test.*

class RuleAlignmentTest {
    @Test fun refitsOuterRulesWithoutNativeInterpolation() {
        val w=800;val h=900;val pixels=ByteArray(w*h){240.toByte()}
        for(line in 0..9) for(at in 0..540) for(delta in -1..1){
            pixels[(150+line*60+delta)*w+100+at]=0
            pixels[(150+at)*w+100+line*60+delta]=0
        }
        val q=Quad(Corner(90.0,140.0),Corner(650.0,140.0),Corner(650.0,700.0),Corner(90.0,700.0))
        val aligned=RuleAlignment.align(GrayImage(w,h,pixels),q)
        assertEquals(100.0,aligned.topLeft.x,2.0);assertEquals(150.0,aligned.topLeft.y,2.0)
        assertEquals(640.0,aligned.bottomRight.x,2.0);assertEquals(690.0,aligned.bottomRight.y,2.0)
        assertEquals(q,RuleAlignment.align(GrayImage(w,h,ByteArray(w*h){240.toByte()}),q))
    }
    @Test fun collapsedOrNonFiniteCandidatesAreReturnedUnaligned() {
        val image=GrayImage(40,40,ByteArray(1600){240.toByte()})
        val point=Corner(10.0,10.0)
        val collapsed=Quad(point,point,point,point)
        assertEquals(collapsed,RuleAlignment.align(image,collapsed))
        val broken=Quad(Corner(Double.NaN,0.0),Corner(30.0,0.0),Corner(30.0,30.0),Corner(0.0,30.0))
        assertSame(broken,RuleAlignment.align(image,broken))
    }
    @Test fun aSinglePixelImageHasNoGridRatherThanAnEndlessSearch() {
        assertNull(GridLineFitter.fit(GrayImage(1,1,ByteArray(1))))
        assertNull(GridLineFitter.fit(GrayImage(1,40,ByteArray(40){ if(it%4==0) 0 else 240.toByte() })))
    }
}
