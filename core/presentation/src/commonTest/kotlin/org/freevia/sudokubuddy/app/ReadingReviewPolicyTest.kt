package org.freevia.sudokubuddy.app

import kotlin.test.*

class ReadingReviewPolicyTest {
    @Test fun confidentReadingsDoNotAcquireReviewAndOriginalQuestionsKeepReceiptsReachable() {
        assertFalse(PuzzleLogic.hasReadingReview(0,0))
        assertTrue(PuzzleLogic.hasReadingReview(1,0))
        assertTrue(PuzzleLogic.hasReadingReview(0,1))
    }
    @Test fun selectionAndTeachingHideExpandedReviewWithoutDiscardingItsState() {
        for(mode in OverlayMode.entries) {
            val expected=mode==OverlayMode.NONE || mode==OverlayMode.READING
            assertEquals(expected,PuzzleLogic.showReadingReview(true,true,false,mode))
            assertFalse(PuzzleLogic.showReadingReview(true,true,true,mode))
            assertFalse(PuzzleLogic.showReadingReview(true,false,false,mode))
        }
    }
}
