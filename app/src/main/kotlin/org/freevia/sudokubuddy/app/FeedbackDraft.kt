package org.freevia.sudokubuddy.app

import java.net.URLEncoder

/** A browser draft, not an upload or a submitted issue. */
internal object FeedbackDraft {
    private const val ISSUE_URL = "https://github.com/freevia-org/sudoku-buddy/issues/new"
    private const val MAX_URL_LENGTH = 7_500

    fun issueUrl(
        versionName: String,
        versionCode: Int,
        device: String,
        androidVersion: String,
        diagnostics: String,
    ): String {
        fun field(value: String): String {
            val text = value.replace('\n', ' ').replace('\r', ' ').take(60)
            return if (text.lastOrNull()?.isHighSurrogate() == true) text.dropLast(1) else text
        }
        val version = "${field(versionName)} ($versionCode)"
        val title = "Sudoku Buddy feedback - $version"
        val report = diagnostics.replace("```", "'''")
        var limit = minOf(report.length, 1_200)
        while (true) {
            // Do not cut an emoji or other surrogate pair in half.
            val end = if (limit > 0 && report[limit - 1].isHighSurrogate()) limit - 1 else limit
            val excerpt = report.take(end)
            val body = buildString {
                appendLine("## Issue or idea")
                appendLine("Please replace this prompt with a full description of the problem or idea. Give this report a short, descriptive title.")
                appendLine()
                appendLine("## Expected behavior or proposed improvement")
                appendLine("What did you expect, or how would your idea help you solve or learn Sudoku?")
                appendLine()
                appendLine("## What happened and steps to reproduce (for bugs)")
                appendLine("Describe what happened and the steps to repeat it. Say whether you checked and corrected the recognized digits first. For an idea, write 'Not applicable'.")
                appendLine()
                appendLine("## Screenshots or puzzle examples")
                appendLine("Please attach relevant screenshots using GitHub's attachment control: the puzzle, recognized digits, Check result, or hint stage that shows the issue. Include expected and actual results where useful.")
                appendLine("No images are attached automatically. Reports and attachments submitted here are public. Use info@freevia.org for examples you prefer to send privately.")
                appendLine()
                appendLine("## App and device (prefilled)")
                appendLine("- App: Sudoku Buddy $version")
                appendLine("- Device: ${field(device)}")
                appendLine("- Android: ${field(androidVersion)}")
                appendLine()
                appendLine("## Recent scan diagnostics (prefilled)")
                appendLine("```text")
                appendLine(excerpt.ifBlank { "No scan diagnostics available." })
                appendLine("```")
                if (end < report.length) appendLine("Diagnostics shortened to fit the browser draft. The app's Send diagnostics action can share the full report.")
            }
            val url = "$ISSUE_URL?title=${encode(title)}&body=${encode(body)}"
            if (url.length <= MAX_URL_LENGTH || limit == 0) return url
            limit = (limit - 100).coerceAtLeast(0)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
