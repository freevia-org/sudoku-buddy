package org.freevia.sudokubuddy.recognize

import org.freevia.sudokubuddy.vision.GrayImage
import org.freevia.sudokubuddy.vision.OpenCvNatives
import org.freevia.sudokubuddy.vision.RgbImage
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Loads exact recorded inference cells; JPEG decoding and geometry are deliberately bypassed. */
class ExactCaptureReplayTest {
    @Test
    fun `replay exact Android inputs and compare stored inference output`() {
        val path = System.getProperty("probe")?.takeIf { it.endsWith("replay.tsv") } ?: return
        val lines = File(path).readLines()
        val header = lines.first().split('\t')
        val records = lines.drop(1).filter { it.isNotBlank() }.map { line ->
            val values = line.split('\t')
            require(values.size == header.size)
            header.zip(values).toMap()
        }
        assertEquals((0 until 81).toList(), records.map { it.getValue("index").toInt() })
        fun bytes(record: Map<String, String>, prefix: String, channels: Int): ByteArray {
            val data = File(record.getValue("${prefix}File")).readBytes()
            val width = record.getValue("${prefix}Width").toInt()
            val height = record.getValue("${prefix}Height").toInt()
            assertEquals(width * height * channels, data.size)
            val hash = MessageDigest.getInstance("SHA-256").digest(data)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            assertEquals(record.getValue("${prefix}Sha256"), hash)
            return data
        }
        val cells = records.map { record -> GrayImage(record.getValue("grayWidth").toInt(),
            record.getValue("grayHeight").toInt(), bytes(record, "gray", 1)) }
        val colors = if (records.all { it.getValue("rgbFile").isEmpty() }) null else records.map { record ->
            RgbImage(record.getValue("rgbWidth").toInt(), record.getValue("rgbHeight").toInt(),
                bytes(record, "rgb", 3))
        }
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val reader = GridReader()
        val result = reader.read(cells, colors)
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
        val outcome = when (result) {
            is ReadResult.Accepted -> "accepted"
            is ReadResult.NeedsConfirmation -> "needs-confirmation"
            is ReadResult.Unreadable -> "unreadable"
        }
        val uncertain = when (result) {
            is ReadResult.Accepted -> emptySet()
            is ReadResult.NeedsConfirmation -> result.uncertainCells
            is ReadResult.Unreadable -> result.uncertainCells
        }
        val ink = CellAnalyzer.inspect(cells)
        val core = reader.findPrintedCore(ink.mapNotNull { it?.blob })
        val differences = mutableListOf<String>()
        if (outcome != records.first().getValue("outcome")) differences += "outcome $outcome"
        File("build/exact-capture-replay.tsv").bufferedWriter().use { out ->
            out.appendLine("index\tstoredInk\tactualInk\tstoredDigit\tactualDigit\tstoredGridRole\tactualGridRole\tstoredGridDigit\tactualGridDigit\tstoredUncertain\tactualUncertain\tmargin\trelHeight\tverticalOffset\tcontrast\tstroke\tcompany\toutshone\tdifferences\tcomponents\tstoredRoleUncertain\tactualRoleUncertain")
            for ((index, record) in records.withIndex()) {
                val reading = readings[index]
                val local = mutableListOf<String>()
                fun check(name: String, actual: String) {
                    if (actual != record.getValue(name)) local += "$name:${record.getValue(name)}->$actual"
                }
                check("ink", reading?.ink?.name ?: "")
                check("digit", reading?.digit?.toString() ?: "")
                check("gridRole", grid?.get(index)?.source?.name ?: "")
                check("gridDigit", grid?.get(index)?.digit?.toString() ?: "")
                check("uncertain", (index in uncertain).toString())
                record["roleUncertain"]?.let { check("roleUncertain", (reading?.roleUncertain ?: false).toString()) }
                val expectedProbabilities = record.getValue("probabilities").takeIf { it.isNotEmpty() }
                    ?.split(',')?.map { it.toFloat() }
                val actualProbabilities = reading?.probabilities
                if (expectedProbabilities == null) {
                    if (actualProbabilities != null) local += "probabilities:unexpected"
                } else if (actualProbabilities == null || actualProbabilities.size != expectedProbabilities.size ||
                    expectedProbabilities.indices.any { abs(expectedProbabilities[it] - actualProbabilities[it]) > 0.0001f }) {
                    local += "probabilities:mismatch"
                }
                for ((field, actual) in listOf("heightRatio" to reading?.heightRatio,
                    "darkness" to reading?.darkness, "colorChroma" to reading?.colorChroma,
                    "margin" to reading?.margin?.toDouble())) {
                    val expected = record.getValue(field).toDoubleOrNull()
                    if ((expected == null) != (actual == null) ||
                        (expected != null && actual != null && abs(expected - actual) > 0.0001)) local += "$field:mismatch"
                }
                if (local.isNotEmpty()) differences += "cell$index ${local.joinToString(",") }"
                val blob = ink[index]?.blob
                out.appendLine(listOf(index, record.getValue("ink"), reading?.ink?.name ?: "",
                    record.getValue("digit"), reading?.digit ?: "", record.getValue("gridRole"),
                    grid?.get(index)?.source?.name ?: "", record.getValue("gridDigit"), grid?.get(index)?.digit ?: "",
                    record.getValue("uncertain"), index in uncertain, reading?.margin ?: "",
                    if (blob == null || core == null) "" else blob.heightRatio / core.height,
                    blob?.verticalOffset ?: "", blob?.contrast ?: "", blob?.strokeWidth ?: "",
                    ink[index]?.company ?: "", ink[index]?.outshoneBy ?: "", local.joinToString(";"),
                    ink[index]?.pieces?.sortedByDescending { it.area }?.joinToString(";") {
                        "${it.left},${it.top},${it.width},${it.height},${it.area},${"%.1f".format(it.contrast)}"
                    } ?: "", record["roleUncertain"] ?: "false", reading?.roleUncertain ?: false).joinToString("\t"))
            }
        }
        println("Exact capture replay: ${differences.size} differences; outcome=$outcome; uncertain=$uncertain")
        // dump=true intentionally reports a candidate policy against its frozen Android
        // baseline. PhysicalPageTruthTest independently checks the blind transcription.
        if (System.getProperty("dump") != "true") assertTrue(differences.isEmpty(), differences.joinToString("\n"))
    }
}
