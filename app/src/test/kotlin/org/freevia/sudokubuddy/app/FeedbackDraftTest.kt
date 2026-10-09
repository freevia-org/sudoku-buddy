package org.freevia.sudokubuddy.app

import java.net.URI
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedbackDraftTest {
    private fun fields(url: String): Map<String, String> = URI(url).rawQuery.split('&').associate {
        val (key, value) = it.split('=', limit = 2)
        key to URLDecoder.decode(value, "UTF-8")
    }

    @Test
    fun `query characters remain in the draft and cannot change destination or parameters`() {
        val url = FeedbackDraft.issueUrl("1.0.0-debug", 123, "Phone A&B + #1", "16", "Glare & blur\nTry again? #grid")
        val uri = URI(url)
        assertEquals("https", uri.scheme)
        assertEquals("github.com", uri.host)
        assertEquals("/freevia-org/sudoku-buddy/issues/new", uri.path)
        val query = fields(url)
        assertEquals(setOf("title", "body"), query.keys)
        val body = query.getValue("body")
        assertTrue(body.contains("1.0.0-debug (123)"))
        assertTrue(body.contains("Phone A&B + #1"))
        assertTrue(body.contains("Glare & blur\nTry again? #grid"))
        assertTrue(body.contains("full description"))
        assertTrue(body.contains("GitHub's attachment control"))
        assertTrue(body.contains("public"))
    }

    @Test
    fun `long Unicode diagnostics fit the URL without losing the report prompts`() {
        val url = FeedbackDraft.issueUrl("1.0.0", 123, "Test phone", "16", "📷漢字".repeat(10_000))
        assertTrue(url.length <= 7_500)
        val body = fields(url).getValue("body")
        assertTrue(body.contains("Diagnostics shortened"))
        assertTrue(body.contains("steps to reproduce"))
        assertFalse(body.contains('\uFFFD'))
        assertFalse(body.contains("?漢字"))
    }

    @Test
    fun `unusually long device fields are bounded as well as diagnostics`() {
        val field = "📷".repeat(1_000)
        val url = FeedbackDraft.issueUrl(field, 123, field, field, field)
        assertTrue(url.length <= 7_500)
        assertFalse(fields(url).getValue("body").contains('\uFFFD'))
    }
}
