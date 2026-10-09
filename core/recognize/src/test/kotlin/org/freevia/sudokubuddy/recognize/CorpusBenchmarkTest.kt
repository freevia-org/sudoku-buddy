package org.freevia.sudokubuddy.recognize

import org.freevia.sudokubuddy.vision.CorpusFixtures
import org.freevia.sudokubuddy.vision.GateVerdict
import org.freevia.sudokubuddy.vision.OpenCvNatives
import org.freevia.sudokubuddy.vision.StructuralGate
import org.freevia.sudokubuddy.vision.ColorCells
import org.freevia.sudokubuddy.vision.RgbImage
import javax.imageio.ImageIO
import java.io.File
import kotlin.test.Test

/** All labelled cells count, including rejected photos, missing ink and editorial marks. */
class CorpusBenchmarkTest {
    @Test
    fun `export comprehensive corpus benchmark`() {
        if (System.getProperty("dump") != "true") return
        // Explicit benchmark requests fail closed; normal CI tests still skip absent
        // local photographs through CorpusFixtures.requireCorpus().
        check(CorpusFixtures.isAvailable) { "Requested benchmark corpus absent at ${CorpusFixtures.directory}" }
        check(CorpusLabels.isAvailable) { "Requested benchmark labels are absent" }
        check(CorpusFixtures.photos.any { CorpusLabels.forPhoto(it.name) != null }) {
            "Requested benchmark has no labelled corpus images"
        }
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val classifier = DigitClassifier.load()
        val reader = GridReader(classifier)
        val labelledOnly = System.getProperty("benchmarkLabelledOnly") == "true"
        val output = File(if (labelledOnly) "build/corpus-benchmark-labelled.tsv" else "build/corpus-benchmark.tsv")
        output.parentFile.mkdirs()
        output.bufferedWriter().use { out ->
            out.appendLine("photo\tcell\tgate\treader\texpectedRole\texpectedDigit\tactualRole\tactualDigit\tclassifierDigit\tinkPresent\teditorial\tcolorRole\tcolorDigit\tchroma\tgrayInk\tcolorInk\tgrayGridRole\tgrayGridDigit\tcolorGridRole\tcolorGridDigit\tcolorReader\tgrayReason\tcolorReason\tscope\tcomponents\tlayoutEvidence\tgrayRoleUncertain\tcolorRoleUncertain")
            for (photo in CorpusFixtures.photos) {
                val truth = CorpusLabels.forPhoto(photo.name)
                if (labelledOnly && truth == null) continue
                val gate = StructuralGate.assess(CorpusFixtures.load(photo))
                val usable = gate as? GateVerdict.Usable
                val result = usable?.let { reader.read(it.cells) }
                val colors = usable?.let {
                    val image = ImageIO.read(photo)
                    val pixels = ByteArray(image.width * image.height * 3)
                    for (y in 0 until image.height) for (x in 0 until image.width) {
                        val rgb = image.getRGB(x, y)
                        val offset = (y * image.width + x) * 3
                        pixels[offset] = (rgb shr 16).toByte()
                        pixels[offset + 1] = (rgb shr 8).toByte()
                        pixels[offset + 2] = rgb.toByte()
                    }
                    ColorCells.extract(RgbImage(image.width, image.height, pixels), it)
                }
                val colored = usable?.let { reader.read(it.cells, colors) }
                val grayReason = (result as? ReadResult.Unreadable)?.reason ?: ""
                val colorReason = (colored as? ReadResult.Unreadable)?.reason ?: ""
                if (truth == null) {
                    out.appendLine(listOf(photo.name, -1, if (usable != null) "usable" else "rejected",
                        result?.javaClass?.simpleName ?: "Unreadable",
                        "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                        colored?.javaClass?.simpleName ?: "Unreadable", grayReason, colorReason,
                        "all_corpus_images", "", "", "", "").joinToString("\t"))
                    continue
                }
                val grayGrid = when (result) {
                    is ReadResult.Accepted -> result.grid
                    is ReadResult.NeedsConfirmation -> result.grid
                    else -> null
                }
                val colorGrid = when (colored) {
                    is ReadResult.Accepted -> colored.grid
                    is ReadResult.NeedsConfirmation -> colored.grid
                    else -> null
                }
                val readings = when (result) {
                    is ReadResult.Accepted -> result.readings
                    is ReadResult.NeedsConfirmation -> result.readings
                    else -> emptyList()
                }.associateBy { it.index }
                val colorReadings = when (colored) {
                    is ReadResult.Accepted -> colored.readings
                    is ReadResult.NeedsConfirmation -> colored.readings
                    else -> emptyList()
                }.associateBy { it.index }
                val ink = usable?.let { CellAnalyzer.inspect(it.cells) }
                for (index in 0 until 81) {
                    val reading = readings[index]
                    val expected = truth[index]
                    val bitmap = ink?.get(index)
                    val colorReading = colorReadings[index]
                    val predicted = bitmap?.let {
                        val probabilities = classifier.classify(it.normalised)
                        probabilities.indices.maxBy { digit -> probabilities[digit] } + 1
                    }
                    val actualRole = when (reading?.ink) {
                        Ink.PRINTED -> "GIVEN"
                        Ink.ANSWER -> "GUESS"
                        Ink.MARK, Ink.NONE -> "EMPTY"
                        null -> "UNREADABLE"
                    }
                    out.appendLine(listOf(
                        photo.name, index, if (usable != null) "usable" else "rejected",
                        result?.javaClass?.simpleName ?: "Unreadable", expected.source,
                        expected.digit ?: "", actualRole, reading?.digit ?: "", predicted ?: "",
                        bitmap != null, photo.name in CorpusLabels.drawnOver,
                        when (colorReading?.ink) {
                            Ink.PRINTED -> "GIVEN"
                            Ink.ANSWER -> "GUESS"
                            Ink.MARK, Ink.NONE -> "EMPTY"
                            null -> "UNREADABLE"
                        }, colorReading?.digit ?: "",
                        if (bitmap != null && colors != null) InkColor.chroma(colors[index], bitmap) else "",
                        reading?.ink ?: "UNREADABLE", colorReading?.ink ?: "UNREADABLE",
                        grayGrid?.get(index)?.source ?: "UNREADABLE", grayGrid?.get(index)?.digit ?: "",
                        colorGrid?.get(index)?.source ?: "UNREADABLE", colorGrid?.get(index)?.digit ?: "",
                        colored?.javaClass?.simpleName ?: "Unreadable",
                        grayReason, colorReason,
                        if (labelledOnly) "labelled_images_only" else "all_corpus_images",
                        bitmap?.pieces?.sortedByDescending { it.area }?.joinToString(";") {
                            "${it.left},${it.top},${it.width},${it.height},${it.area},${"%.1f".format(it.contrast)}"
                        } ?: "",
                        bitmap?.let(InkLayout::evidence) ?: "", reading?.roleUncertain ?: "", colorReading?.roleUncertain ?: "",
                    ).joinToString("\t"))
                }
            }
        }
        println("comprehensive corpus benchmark: ${output.absolutePath}")
    }
}
