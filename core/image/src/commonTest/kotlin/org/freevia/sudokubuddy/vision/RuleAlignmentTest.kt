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
}
