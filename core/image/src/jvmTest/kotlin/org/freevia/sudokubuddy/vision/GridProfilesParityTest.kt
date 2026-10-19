package org.freevia.sudokubuddy.vision

import kotlin.random.Random
import kotlin.test.*

class GridProfilesParityTest {
    @Test fun portableLineProfilesMatchOpenCvIncludingReplicatedBorders() {
        nu.pattern.OpenCV.loadLocally()
        val random=Random(946)
        for((w,h) in listOf(32 to 32,73 to 91,127 to 125,550 to 550)) {
            val image=GrayImage(w,h,ByteArray(w*h){random.nextInt(256).toByte()})
            val reference=openCvGridInkProfiles(image);val shared=gridInkProfiles(image)
            assertContentEquals(reference.first,shared.first,"columns ${w}x$h")
            assertContentEquals(reference.second,shared.second,"rows ${w}x$h")
        }
    }
}
