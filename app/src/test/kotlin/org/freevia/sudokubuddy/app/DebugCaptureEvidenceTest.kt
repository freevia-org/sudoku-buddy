package org.freevia.sudokubuddy.app

import java.io.File
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugCaptureEvidenceTest {
    @TempDir
    lateinit var temporary: File

    private fun capture(root: File, time: Long): File =
        File(root, "capture-$time-1234abcd").apply {
            check(mkdir())
            File(this, "original.jpg").writeBytes(byteArrayOf(1, 2, 3, -1))
        }

    @Test
    fun `only last three recorder directories retained and other history untouched`() {
        val root = File(temporary, "debug-capture-evidence").apply { check(mkdir()) }
        val captures = (0L..3L).map { capture(root, 1_700_000_000_000L + it) }
        val history = File(root, "puzzle-history").apply { check(mkdir()) }
        File(history, "saved.jpg").writeBytes(byteArrayOf(9))
        val foreign = File(root, "scan-20260101-refused.jpg").apply { writeBytes(byteArrayOf(8)) }
        DebugCaptureEvidence.prune(root)
        assertFalse(captures.first().exists())
        for (capture in captures.drop(1)) {
            assertContentEquals(byteArrayOf(1, 2, 3, -1), File(capture, "original.jpg").readBytes())
        }
        assertContentEquals(byteArrayOf(9), File(history, "saved.jpg").readBytes())
        assertContentEquals(byteArrayOf(8), foreign.readBytes())
    }

    @Test
    fun `pruning never recurses into or deletes unknown data inside a matching directory`() {
        val root = File(temporary, "debug-capture-evidence").apply { check(mkdir()) }
        val protected = capture(root, 1_600_000_000_000L)
        val nested = File(protected, "other-data").apply { check(mkdir()) }
        val history = File(nested, "history.json").apply { writeText("preserve") }
        repeat(3) { capture(root, 1_700_000_000_000L + it) }
        DebugCaptureEvidence.prune(root)
        assertTrue(protected.exists())
        assertTrue(history.readText() == "preserve")
        assertContentEquals(byteArrayOf(1, 2, 3, -1), File(protected, "original.jpg").readBytes())
    }
}
