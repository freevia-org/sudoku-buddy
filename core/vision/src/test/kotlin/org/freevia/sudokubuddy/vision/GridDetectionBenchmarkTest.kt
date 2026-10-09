package org.freevia.sudokubuddy.vision

import org.opencv.core.Core
import java.io.File
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GridDetectionBenchmarkTest {
    @Test
    fun `records every corpus detection and geometry without stopping at first miss`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val previousThreads = Core.getNumThreads()
        Core.setNumThreads(1)
        try {
            val rows = CorpusFixtures.photos.map { file ->
                val image = CorpusFixtures.load(file)
                lateinit var location: GridLocation
                val elapsed = measureNanoTime { location = GridLocator.locate(image) } / 1_000_000.0
                val found = location as? GridLocation.Found
                val geometry = found?.let { GridLineFitter.fit(it.rectified) }
                "${file.name}\t${found != null}\t${found?.gridScore ?: 0.0}\t${geometry != null}\t$elapsed"
            }
            File("build/grid-detection-benchmark.tsv").writeText(
                "photo\tfound\tscore\tfittedGeometry\tmilliseconds\n" + rows.joinToString("\n") + "\n",
            )
            val detected = rows.count { it.split('\t')[1] == "true" }
            println("Corpus detection: $detected/${rows.size}; report build/grid-detection-benchmark.tsv")
            assertTrue(detected == rows.size, rows.filter { it.split('\t')[1] == "false" }.joinToString("\n"))
        } finally {
            Core.setNumThreads(previousThreads)
        }
    }

    @Test
    fun `annotated screenshot grids retain all nine rows and columns`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val previousThreads = Core.getNumThreads()
        Core.setNumThreads(1)
        try {
        for ((fragment, top) in listOf("44fd102" to 180.0, "5c2932" to 218.0)) {
            val image = CorpusFixtures.photo(fragment)
            val found = assertIs<GridLocation.Found>(GridLocator.locate(image), fragment)
            assertTrue(kotlin.math.abs(found.quad.topLeft.y - top) < 25, "$fragment: ${found.quad}")
            assertTrue(found.quad.area / (image.width * image.height) > 0.35, "$fragment lost part of grid")
            assertTrue(GridLineFitter.fit(found.rectified) != null, "$fragment has no fitted grid geometry")
        }
        } finally {
            Core.setNumThreads(previousThreads)
        }
    }
}
