package org.freevia.sudokubuddy.app

import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AtomicStorageTest {
    @Test
    fun `interrupted writes preserve the previous saved puzzle`() {
        val directory = Files.createTempDirectory("sudoku-history").toFile()
        try {
            val file = directory.resolve("123.txt").apply { writeText("previous puzzle") }
            assertFailsWith<IOException> {
                atomicWrite(file) { output ->
                    output.write("partial".toByteArray())
                    throw IOException("disk full")
                }
            }
            assertEquals("previous puzzle", file.readText())
            assertEquals(listOf("123.txt"), directory.list()!!.toList())
            atomicWrite(file) { it.write("corrected puzzle".toByteArray()) }
            assertEquals("corrected puzzle", file.readText())
        } finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }

    @Test
    fun `only stale temporaries of a killed write are swept`() {
        val directory = Files.createTempDirectory("sudoku-history").toFile()
        try {
            val now = 10_000_000_000L
            val saved = directory.resolve("123.jpg").apply { writeText("photo"); setLastModified(1_000L) }
            val abandoned = directory.resolve("123.jpg456.pending")
                .apply { writeText("half"); setLastModified(now - 2 * 60 * 60 * 1000L) }
            val inFlight = directory.resolve("124.txt789.pending")
                .apply { writeText("writing"); setLastModified(now - 1_000L) }
            sweepAbandonedWrites(directory, now)
            assertEquals(false, abandoned.exists())
            assertEquals(true, inFlight.exists())
            assertEquals("photo", saved.readText())
        } finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }
}
