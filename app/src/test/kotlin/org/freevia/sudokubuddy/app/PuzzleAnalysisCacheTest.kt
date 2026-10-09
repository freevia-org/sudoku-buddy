package org.freevia.sudokubuddy.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PuzzleAnalysisCacheTest {
    @Test
    fun `an unavailable analysis is cached too`() {
        val cache = PuzzleAnalysisCache<String, String?>(2)
        assertNull(cache.get("unsolvable") { null })
        assertNull(cache.get("unsolvable") { error("null results must not be recomputed") })
    }

    @Test
    fun `failed work does not poison another puzzle or prevent retry`() {
        val cache = PuzzleAnalysisCache<String, String>(2)
        assertEquals("first route", cache.get("first") { "first route" })
        assertFailsWith<IllegalStateException> { cache.get("second") { error("failed") } }
        assertEquals("second route", cache.get("second") { "second route" })
        assertEquals("first route", cache.get("first") { error("first was overwritten") })
    }

    @Test
    fun `recent puzzles remain while older entries are evicted`() {
        val cache = PuzzleAnalysisCache<String, Int>(2)
        cache.get("first") { 1 }
        cache.get("second") { 2 }
        cache.get("first") { error("first should be cached") }
        cache.get("third") { 3 }
        assertEquals(1, cache.get("first") { error("recent entry was evicted") })
        assertEquals(22, cache.get("second") { 22 })
    }

    @Test
    fun `a slow analysis does not block access to an already prepared puzzle`() {
        val cache = PuzzleAnalysisCache<String, Int>(2)
        cache.get("ready") { 7 }
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val slow = workers.submit<Int> {
                cache.get("slow") {
                    started.countDown()
                    assertTrue(finish.await(5, TimeUnit.SECONDS))
                    8
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val ready = workers.submit<Int> { cache.get("ready") { error("cached") } }
            assertEquals(7, ready.get(2, TimeUnit.SECONDS))
            finish.countDown()
            assertEquals(8, slow.get(5, TimeUnit.SECONDS))
        } finally {
            finish.countDown()
            workers.shutdownNow()
        }
    }
}
