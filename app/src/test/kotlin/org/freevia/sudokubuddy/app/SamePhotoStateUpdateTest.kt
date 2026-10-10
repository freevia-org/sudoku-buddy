package org.freevia.sudokubuddy.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SamePhotoStateUpdateTest {
    private data class FakePuzzle(val photo: Any, val trainingConsent: Boolean)

    @Test
    fun `queued old report cannot clear or change the current puzzle`() {
        val oldPhoto = Any()
        val currentPhoto = Any()
        val currentPuzzle = FakePuzzle(currentPhoto, trainingConsent = false)

        val updated = updateIfSamePhoto(
            current = currentPuzzle,
            expectedPhoto = oldPhoto,
            photoOf = FakePuzzle::photo,
        ) { it.copy(trainingConsent = true) }

        assertSame(currentPuzzle, updated)
        assertEquals(false, updated?.trainingConsent)
    }

    @Test
    fun `report update applies when its own photo is still current`() {
        val photo = Any()
        val currentPuzzle = FakePuzzle(photo, trainingConsent = false)

        val updated = updateIfSamePhoto(
            current = currentPuzzle,
            expectedPhoto = photo,
            photoOf = FakePuzzle::photo,
        ) { it.copy(trainingConsent = true) }

        assertEquals(true, updated?.trainingConsent)
    }
}
