package org.freevia.sudokubuddy.app

import android.content.Context
import android.util.Log
import org.freevia.sudokubuddy.BuildConfig
import org.freevia.sudokubuddy.model.Grid
import org.freevia.sudokubuddy.recognize.CellReading
import org.freevia.sudokubuddy.recognize.ReadResult
import org.freevia.sudokubuddy.vision.GateVerdict
import org.freevia.sudokubuddy.vision.RgbImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Debug-only byte-exact inputs/results, separate from puzzle history and sharing. */
internal object DebugCaptureEvidence {
    private const val KEEP = 3
    private val captureName = Regex("capture-[0-9]{13}-[0-9a-f]{8}")
    private val evidenceName = Regex("original\\.jpg|rectified\\.gray|manifest\\.json|cell-[0-9]{2}\\.(gray|rgb)")

    fun keep(
        context: Context,
        originalJpeg: ByteArray,
        rotationDegrees: Int,
        capturedAtMillis: Long,
        sourceWidth: Int,
        sourceHeight: Int,
        verdict: GateVerdict.Usable,
        colorCells: List<RgbImage>?,
        result: ReadResult,
    ) {
        if (!BuildConfig.DEBUG) return
        val root = File(context.filesDir, "debug-capture-evidence")
        var created: File? = null
        runCatching {
            check(root.mkdirs() || root.isDirectory)
            val id = "capture-$capturedAtMillis-${UUID.randomUUID().toString().take(8)}"
            val folder = File(root, id)
            check(folder.mkdir())
            created = folder
            val readings = when (result) {
                is ReadResult.Accepted -> result.readings
                is ReadResult.NeedsConfirmation -> result.readings
                is ReadResult.Unreadable -> emptyList()
            }.associateBy { it.index }
            val grid = when (result) {
                is ReadResult.Accepted -> result.grid
                is ReadResult.NeedsConfirmation -> result.grid
                is ReadResult.Unreadable -> null
            }
            val cells = JSONArray()
            for ((index, image) in verdict.cells.withIndex()) {
                val bounds = verdict.geometry.cellBounds(index)
                val record = JSONObject()
                    .put("index", index).put("row", index / 9 + 1).put("column", index % 9 + 1)
                    .put("bounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
                    .put("gray", raw(folder, "cell-%02d.gray".format(index), image.pixels,
                        image.width, image.height, "gray8-row-major"))
                    .put("reading", readings[index]?.let(::reading) ?: JSONObject.NULL)
                colorCells?.get(index)?.let { color ->
                    record.put("rgb", raw(folder, "cell-%02d.rgb".format(index), color.pixels,
                        color.width, color.height, "rgb8-interleaved-row-major"))
                }
                grid?.let { record.put("gridCell", gridCell(it, index)) }
                cells.put(record)
            }
            val uncertain = when (result) {
                is ReadResult.Accepted -> emptySet()
                is ReadResult.NeedsConfirmation -> result.uncertainCells
                is ReadResult.Unreadable -> result.uncertainCells
            }
            val outcome = when (result) {
                is ReadResult.Accepted -> "accepted"
                is ReadResult.NeedsConfirmation -> "needs-confirmation"
                is ReadResult.Unreadable -> "unreadable"
            }
            val reason = when (result) {
                is ReadResult.Accepted -> null
                is ReadResult.NeedsConfirmation -> result.reason
                is ReadResult.Unreadable -> result.reason
            }
            val quality = verdict.quality
            val manifest = JSONObject()
                .put("schemaVersion", 1).put("captureId", id)
                .put("capturedAtMillis", capturedAtMillis)
                .put("capturedAtUtc", Instant.ofEpochMilli(capturedAtMillis).toString())
                .put("debugInstrumentation", true)
                .put("instrumentationNote", "Disk evidence retention affects timing; not a latency benchmark.")
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("appVersionCode", BuildConfig.VERSION_CODE)
                .put("originalJpeg", raw(folder, "original.jpg", originalJpeg, null, null, "original-jpeg"))
                .put("rotationDegrees", rotationDegrees)
                .put("sourceWidth", sourceWidth).put("sourceHeight", sourceHeight)
                .put("quad", JSONArray(verdict.quad.corners.map { corner ->
                    JSONArray(listOf(corner.x, corner.y))
                }))
                .put("quadOrder", "topLeft,topRight,bottomRight,bottomLeft")
                .put("gridScore", verdict.gridScore).put("curved", verdict.curved)
                .put("framingComplaint", verdict.complaint?.message ?: JSONObject.NULL)
                .put("verticalLines", JSONArray(verdict.geometry.verticalLines.toList()))
                .put("horizontalLines", JSONArray(verdict.geometry.horizontalLines.toList()))
                .put("rectified", raw(folder, "rectified.gray", verdict.rectified.pixels,
                    verdict.rectified.width, verdict.rectified.height, "gray8-row-major"))
                .put("quality", JSONObject().put("sharpness", quality.sharpness)
                    .put("meanLuma", quality.meanLuma)
                    .put("clippedWhiteFraction", quality.clippedWhiteFraction)
                    .put("worstQuadrantSharpnessRatio", quality.worstQuadrantSharpnessRatio))
                .put("outcome", outcome).put("reason", reason ?: JSONObject.NULL)
                .put("uncertainCells", JSONArray(uncertain.sorted()))
                .put("readingCount", readings.size)
                .put("readingsUnavailableReason", if (result is ReadResult.Unreadable) {
                    "Unreadable exposes no CellReading list; exact input cells and outcome are retained."
                } else JSONObject.NULL)
                .put("indexBase", 0).put("rowColumnBase", 1)
                .put("digitProbabilityOrder", JSONArray((1..9).toList()))
                .put("cells", cells)
            // Written last: its presence identifies a complete, replayable capture.
            File(folder, "manifest.json").writeText(manifest.toString(2))
            prune(root)
        }.onFailure { error ->
            runCatching { created?.let { removeOwned(root, it) } }
            runCatching { Log.w("CaptureEvidence", "Could not retain debug capture evidence", error) }
        }
    }

    private fun raw(
        folder: File, name: String, bytes: ByteArray, width: Int?, height: Int?, encoding: String,
    ): JSONObject {
        File(folder, name).writeBytes(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return JSONObject().put("file", name).put("bytes", bytes.size)
            .put("sha256", digest).put("encoding", encoding)
            .put("width", width ?: JSONObject.NULL).put("height", height ?: JSONObject.NULL)
    }

    private fun reading(reading: CellReading): JSONObject = JSONObject()
        .put("ink", reading.ink.name).put("digit", reading.digit ?: JSONObject.NULL)
        .put("roleUncertain", reading.roleUncertain)
        .put("probabilities", reading.probabilities?.let { p ->
            JSONArray(p.map { it.toDouble() })
        } ?: JSONObject.NULL)
        .put("confidence", reading.probabilities?.maxOrNull()?.toDouble() ?: JSONObject.NULL)
        .put("margin", reading.margin.toDouble()).put("heightRatio", reading.heightRatio)
        .put("darkness", reading.darkness).put("colorChroma", reading.colorChroma ?: JSONObject.NULL)

    private fun gridCell(grid: Grid, index: Int): JSONObject = JSONObject()
        .put("digit", grid[index].digit ?: JSONObject.NULL).put("source", grid[index].source.name)

    /** Only directories generated by this helper, never history or arbitrary siblings. */
    internal fun prune(root: File) {
        root.listFiles()?.filter { owned(root, it) }?.sortedByDescending { it.name }
            ?.drop(KEEP)?.forEach { removeOwned(root, it) }
    }

    private fun owned(root: File, child: File): Boolean =
        child.isDirectory && !Files.isSymbolicLink(child.toPath()) && captureName.matches(child.name) &&
            child.canonicalFile.parentFile == root.canonicalFile

    private fun removeOwned(root: File, child: File) {
        if (!owned(root, child)) return
        val files = child.listFiles() ?: return
        // Evidence is deliberately flat. Do not traverse directories or links, or
        // remove a directory containing anything this recorder did not generate.
        if (files.any { !it.isFile || Files.isSymbolicLink(it.toPath()) ||
                !evidenceName.matches(it.name) || it.canonicalFile.parentFile != child.canonicalFile }) return
        files.forEach { it.delete() }
        child.delete()
    }
}
