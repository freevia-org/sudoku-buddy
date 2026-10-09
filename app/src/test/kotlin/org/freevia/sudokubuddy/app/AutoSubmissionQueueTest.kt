package org.freevia.sudokubuddy.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AutoSubmissionQueueTest {
    private val photoA = Any()
    private val photoB = Any()

    @Test
    fun `newer corrections coalesce while separate puzzles stay queued`() {
        val queue = AutoSubmissionQueue<String>()
        queue.offer(1, photoA, 2, "first revision")
        queue.offer(2, photoB, 0, "other puzzle")
        queue.offer(1, photoA, 3, "latest revision")

        val first = queue.poll()!!
        assertEquals(1, first.entryId)
        assertEquals(3, first.revision)
        assertEquals("latest revision", first.payload)
        assertEquals(2, queue.poll()!!.entryId)
        assertNull(queue.poll())
        assertTrueEmpty(queue)
    }

    @Test
    fun `acknowledgement removes only revisions already included`() {
        val queue = AutoSubmissionQueue<String>()
        queue.offer(1, photoA, 3, "new edit")
        queue.markSubmitted(1, 2)
        assertEquals(3, queue.poll()!!.revision)

        queue.offer(1, photoA, 3, "same revision")
        queue.markSubmitted(1, 3)
        assertNull(queue.poll())
    }

    @Test
    fun `newer reopened photo replaces earlier pending snapshot and opt out clears queue`() {
        val queue = AutoSubmissionQueue<String>()
        queue.offer(1, photoA, 4, "old photo")
        queue.offer(1, photoB, 5, "replacement photo")
        val item = queue.poll()!!
        assertSame(photoB, item.photo)
        assertEquals("replacement photo", item.payload)
        queue.offer(2, photoA, 1, "pending")
        queue.clear()
        assertNull(queue.poll())
        assertTrueEmpty(queue)
    }

    @Test
    fun `reopening an entry cannot replace a newer queued correction with stale state`() {
        val queue = AutoSubmissionQueue<String>()
        queue.offer(1, photoA, 5, "newer correction")
        queue.offer(1, photoB, 2, "stale reopened snapshot")
        val item = queue.poll()!!
        assertEquals(5, item.revision)
        assertEquals("newer correction", item.payload)
    }

    @Test
    fun `acknowledgement from a reopened photo clears the same stable entry`() {
        val queue = AutoSubmissionQueue<String>()
        queue.offer(1, photoA, 5, "pending")
        queue.markSubmitted(1, 5)
        assertNull(queue.poll())
    }

    @Test
    fun `failed revision is discarded without dropping a newer correction`() {
        val queue = AutoSubmissionQueue<String>()
        queue.offer(1, photoA, 4, "failed revision")
        queue.discardThrough(1, 4)
        assertNull(queue.poll())

        queue.offer(1, photoA, 5, "newer correction")
        queue.offer(2, photoB, 0, "another puzzle")
        queue.discardThrough(1, 4)
        assertEquals("newer correction", queue.poll()!!.payload)
        assertEquals("another puzzle", queue.poll()!!.payload)
        assertNull(queue.poll())
    }

    private fun assertTrueEmpty(queue: AutoSubmissionQueue<*>) {
        assertTrue(queue.isEmpty)
    }
}
