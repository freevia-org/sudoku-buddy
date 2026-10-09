package org.freevia.sudokubuddy.app

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.freevia.sudokubuddy.BuildConfig
import org.freevia.sudokubuddy.model.Grid
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Creates the private, whole-puzzle training example sent after explicit consent. */
internal object MisreadSubmission {
    val endpoint: String get() = BuildConfig.MISREAD_SUBMISSION_ENDPOINT.trim()
    val available: Boolean get() = endpoint.startsWith("https://")

    fun packageReport(state: PuzzleState, jpeg: ByteArray, version: String): ByteArray {
        require(jpeg.isNotEmpty())
        require(state.originalReports == null || state.originalReports.size == 81)
        val manifest = buildString {
            append("{\"schemaVersion\":1,\"appVersion\":${quoted(version)},")
            append("\"originalGrid\":${quoted(gridText(state.originalGrid))},")
            append("\"currentGrid\":${quoted(gridText(state.grid))},")
            append("\"uncertainCells\":[${state.originalUncertainCells.sorted().joinToString(",")}],")
            append("\"readings\":[")
            append(List(81) { state.originalReports?.get(it) }.mapIndexed { index, report ->
                "{\"cell\":$index,\"ink\":${report?.let { quoted(it.ink.name) } ?: "null"}," +
                    "\"digit\":${report?.digit ?: "null"},\"confidence\":${report?.confidence ?: "null"}," +
                    "\"runnerUp\":${report?.runnerUp ?: "null"}," +
                    "\"runnerUpConfidence\":${report?.runnerUpConfidence ?: "null"}," +
                    "\"roleUncertain\":${report?.roleUncertain ?: false}}"
            }.joinToString(","))
            append("],\"corrections\":[")
            append(state.readingCorrections.map { correction ->
                "{\"cell\":${correction.index},\"oldDigit\":${correction.oldDigit ?: "null"}," +
                    "\"newDigit\":${correction.newDigit ?: "null"}," +
                    "\"oldSource\":${quoted(correction.oldSource)}," +
                    "\"newSource\":${quoted(correction.newSource)},\"atMillis\":${correction.atMillis}}"
            }.joinToString(","))
            append("],\"gridLines\":{\"vertical\":[${state.lines.vertical.joinToString(",") }],")
            append("\"horizontal\":[${state.lines.horizontal.joinToString(",")} ]}}")
        }
        return zipReport(manifest, jpeg)
    }

    internal fun zipReport(manifest: String, jpeg: ByteArray): ByteArray = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json").apply { time = 0L })
                zip.write(manifest.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("puzzle.jpg").apply { time = 0L })
                zip.write(jpeg)
                zip.closeEntry()
            }
        }.toByteArray()

    fun jpeg(state: PuzzleState): ByteArray = ByteArrayOutputStream().use { output ->
        check(state.photo.compress(Bitmap.CompressFormat.JPEG, 92, output))
        output.toByteArray()
    }

    private fun gridText(grid: Grid): String = buildString {
        for (index in 0 until 81) {
            if (index > 0 && index % 9 == 0) append('\n')
            val cell = grid[index]
            append(if (cell.isFilled) cell.digit else '.')
        }
    }

    private fun quoted(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }
}

/** A one-shot POST; callers run this on IO and only after the user opted in. */
internal object MisreadUploader {
    suspend fun upload(state: PuzzleState): Result<SubmissionReceipt> = withContext(Dispatchers.IO) {
        runCatching {
            check(MisreadSubmission.available) { "Submission service is not configured." }
            val body = MisreadSubmission.packageReport(
                state, MisreadSubmission.jpeg(state), BuildConfig.VERSION_NAME,
            )
            check(body.size <= 20 * 1024 * 1024) { "The report is too large to submit." }
            val connection = URL(MisreadSubmission.endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 12_000
                connection.readTimeout = 20_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/zip")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
                val httpStatus = connection.responseCode
                check(httpStatus == 200 || httpStatus == 201) {
                    "Submission service returned HTTP $httpStatus."
                }
                parseReceipt(httpStatus, connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }
    }

    internal fun parseReceipt(httpStatus: Int, json: String): SubmissionReceipt {
        fun value(key: String): String = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
            .find(json)?.groupValues?.get(1) ?: error("Submission response is missing $key.")
        val digest = value("receipt").lowercase()
        val status = value("status")
        check(digest.matches(Regex("[a-f0-9]{64}"))) { "Submission receipt is invalid." }
        check((httpStatus == 201 && status == "accepted") ||
            (httpStatus == 200 && status == "already_received")) {
            "Submission response status is invalid."
        }
        return SubmissionReceipt(digest, status, System.currentTimeMillis())
    }
}
