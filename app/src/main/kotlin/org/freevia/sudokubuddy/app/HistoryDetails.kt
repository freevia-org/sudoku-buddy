package org.freevia.sudokubuddy.app

import java.io.StringReader
import java.io.StringWriter
import java.util.Properties
import org.freevia.sudokubuddy.recognize.Ink
import org.freevia.sudokubuddy.model.Grid

data class ReadingCorrection(
    val index: Int,
    val oldDigit: Int?,
    val newDigit: Int?,
    val oldSource: String,
    val newSource: String,
    val atMillis: Long,
) {
    init {
        require(index in 0..80)
        require(oldDigit == null || oldDigit in 1..9)
        require(newDigit == null || newDigit in 1..9)
        require(atMillis >= 0)
    }
}

data class SubmissionReceipt(
    val digest: String,
    val status: String,
    val receivedAtMillis: Long,
) {
    init {
        require(digest.matches(Regex("[a-f0-9]{64}")))
        require(status == "accepted" || status == "already_received")
        require(receivedAtMillis >= 0)
    }
}

/** Reading geometry and corrections belong to the saved puzzle, not just its screen. */
data class HistoryDetails(
    val entered: Set<Int> = emptySet(),
    val uncertain: Set<Int> = emptySet(),
    val lines: GridLines = GridLines.EVEN,
    val reports: List<CellReport?>? = null,
    val framingNote: String? = null,
    val readerComplaint: String? = null,
    val originalGrid: Grid? = null,
    val originalReports: List<CellReport?>? = null,
    val originalUncertain: Set<Int> = emptySet(),
    val corrections: List<ReadingCorrection> = emptyList(),
    val submittedCorrectionCount: Int = -1,
    val receipts: List<SubmissionReceipt> = emptyList(),
) {
    fun encode(): String = Properties().apply {
        setProperty("entered", entered.sorted().joinToString(","))
        setProperty("uncertain", uncertain.sorted().joinToString(","))
        setProperty("vertical", lines.vertical.joinToString(","))
        setProperty("horizontal", lines.horizontal.joinToString(","))
        framingNote?.let { setProperty("framing", it) }
        readerComplaint?.let { setProperty("complaint", it) }
        originalGrid?.let { setProperty("originalGrid", HistoryFormat.encode(it)) }
        originalReports?.let { setProperty("originalReports", encodeReports(it)) }
        setProperty("originalUncertain", originalUncertain.sorted().joinToString(","))
        if (corrections.isNotEmpty()) setProperty("corrections", corrections.joinToString(";") {
            listOf(it.index, it.oldDigit ?: 0, it.newDigit ?: 0, it.oldSource, it.newSource,
                it.atMillis).joinToString(",")
        })
        if (submittedCorrectionCount >= 0) setProperty("submittedCorrections", submittedCorrectionCount.toString())
        if (receipts.isNotEmpty()) setProperty("receipts", receipts.joinToString(";") {
            "${it.digest},${it.status},${it.receivedAtMillis}"
        })
        reports?.let { values ->
            setProperty("reports", encodeReports(values))
        }
    }.let { properties -> StringWriter().also { properties.store(it, null) }.toString() }

    companion object {
        fun of(state: PuzzleState) = HistoryDetails(state.entered, state.uncertainCells,
            state.lines, state.reports, state.framingNote, state.readerComplaint,
            state.originalGrid, state.originalReports, state.originalUncertainCells,
            state.readingCorrections, state.submittedCorrectionCount, state.submissionReceipts)

        private fun encodeReports(values: List<CellReport?>) = values.joinToString(";") { report ->
            report?.let {
                listOf(it.ink.name, it.digit ?: "", it.confidence,
                    it.runnerUp ?: "", it.runnerUpConfidence, it.roleUncertain).joinToString(",")
            } ?: "-"
        }

        fun decode(text: String): HistoryDetails {
            val properties = Properties().apply { load(StringReader(text)) }
            fun indices(key: String) = properties.getProperty(key, "").split(',')
                .filter(String::isNotEmpty).map(String::toInt).toSet()
                .also { require(it.all { index -> index in 0..80 }) }
            fun lines(key: String) = properties.getProperty(key).split(',').map(String::toFloat)
                .also { values ->
                    // A fitted outer rule may fall slightly beyond the rectified crop.
                    require(values.size == 10 && values.all { it.isFinite() && it in -0.1f..1.1f })
                    require(values.zipWithNext().all { (a, b) -> a < b })
                }
            fun decodeReports(key: String) = properties.getProperty(key)?.split(';')?.map { value ->
                if (value == "-") null else {
                    val parts = value.split(',')
                    require(parts.size == 5 || parts.size == 6)
                    fun digit(at: Int) = parts[at].takeIf(String::isNotEmpty)?.toInt()
                        ?.also { require(it in 1..9) }
                    fun confidence(at: Int) = parts[at].toFloat()
                        .also { require(it.isFinite() && it in 0f..1f) }
                    // Older saved reports predate the note-versus-answer question.
                    val roleUncertain = parts.getOrNull(5)?.toBooleanStrict() ?: false
                    CellReport(Ink.valueOf(parts[0]), digit(1), confidence(2), digit(3), confidence(4), roleUncertain)
                }
            }?.also { require(it.size == 81) }
            val originalGrid = properties.getProperty("originalGrid")?.let(HistoryFormat::decode)
            val corrections = properties.getProperty("corrections", "").split(';')
                .filter(String::isNotEmpty).map { value ->
                    val parts = value.split(',')
                    require(parts.size == 6)
                    fun digit(at: Int) = parts[at].toInt().takeIf { it != 0 }
                    ReadingCorrection(parts[0].toInt(), digit(1), digit(2), parts[3], parts[4], parts[5].toLong())
                }
            val submitted = (properties.getProperty("submittedCorrections")?.toInt() ?: -1)
                .also { require(it >= -1) }
            val originalUncertain = properties.getProperty("originalUncertain", "").split(',')
                .filter(String::isNotEmpty).map(String::toInt).toSet()
                .also { require(it.all { index -> index in 0..80 }) }
            val receipts = properties.getProperty("receipts", "").split(';')
                .filter(String::isNotEmpty).map { value ->
                    val parts = value.split(',')
                    require(parts.size == 3)
                    SubmissionReceipt(parts[0], parts[1], parts[2].toLong())
                }
            return HistoryDetails(indices("entered"), indices("uncertain"),
                GridLines(lines("vertical"), lines("horizontal")), decodeReports("reports"),
                properties.getProperty("framing"), properties.getProperty("complaint"),
                originalGrid, decodeReports("originalReports"), originalUncertain,
                corrections, submitted, receipts)
        }
    }
}
