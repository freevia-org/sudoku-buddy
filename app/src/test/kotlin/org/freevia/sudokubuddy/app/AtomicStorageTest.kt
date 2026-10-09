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
}
