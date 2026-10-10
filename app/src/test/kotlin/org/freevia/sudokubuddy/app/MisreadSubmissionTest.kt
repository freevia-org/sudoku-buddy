package org.freevia.sudokubuddy.app

import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MisreadSubmissionTest {
    @Test
    fun `identical report content makes byte identical retry package`() {
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
        val first = MisreadSubmission.zipReport("{\"schemaVersion\":1}", jpeg)
        val retried = MisreadSubmission.zipReport("{\"schemaVersion\":1}", jpeg)

        assertContentEquals(first, retried)
        ZipInputStream(first.inputStream()).use { zip ->
            assertEquals("manifest.json", zip.nextEntry.name)
            assertEquals("{\"schemaVersion\":1}", zip.readBytes().decodeToString())
            assertEquals("puzzle.jpg", zip.nextEntry.name)
            assertContentEquals(jpeg, zip.readBytes())
        }
    }

    @Test
    fun `upload receipt must match worker status and a SHA256 content id`() {
        val digest = "a".repeat(64)
        assertEquals("accepted", MisreadUploader.parseReceipt(
            201, "{\"receipt\":\"$digest\",\"status\":\"accepted\"}",
        ).status)
        assertEquals("already_received", MisreadUploader.parseReceipt(
            200, "{\"receipt\":\"$digest\",\"status\":\"already_received\"}",
        ).status)
        assertFailsWith<IllegalStateException> {
            MisreadUploader.parseReceipt(201, "{\"receipt\":\"bad\",\"status\":\"accepted\"}")
        }
        assertFailsWith<IllegalStateException> {
            MisreadUploader.parseReceipt(201, "{\"receipt\":\"$digest\",\"status\":\"already_received\"}")
        }
    }

    @Test
    fun `training consent header is sent only for an explicitly opted in report`() {
        assertEquals("yes", MisreadUploader.trainingConsentHeader(true))
        assertEquals(null, MisreadUploader.trainingConsentHeader(false))
    }

    @Test
    fun `deletion response must confirm the exact receipt and completed status`() {
        val receipt = "b".repeat(64)
        MisreadUploader.parseDeletionResponse(
            200, "{\"receipt\":\"$receipt\",\"status\":\"deleted\"}", receipt,
        )
        MisreadUploader.parseDeletionResponse(
            200, "{\"receipt\":\"$receipt\",\"status\":\"already_deleted\"}", receipt,
        )
        assertFailsWith<IllegalStateException> {
            MisreadUploader.parseDeletionResponse(
                200, "{\"receipt\":\"${"c".repeat(64)}\",\"status\":\"deleted\"}", receipt,
            )
        }
        assertFailsWith<IllegalStateException> {
            MisreadUploader.parseDeletionResponse(
                200, "{\"receipt\":\"$receipt\",\"status\":\"queued\"}", receipt,
            )
        }
        assertFailsWith<IllegalStateException> {
            MisreadUploader.parseDeletionResponse(
                503, "{\"receipt\":\"$receipt\",\"status\":\"deleted\"}", receipt,
            )
        }
    }
}
