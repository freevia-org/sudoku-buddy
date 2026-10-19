package org.freevia.sudokubuddy.app

import android.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import org.freevia.sudokubuddy.vision.OpenCvNatives
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Actual JNI and production JPEG-import tests; photos stay in this local emulator. */
class NativeVisionParityTest {
    private fun load() = OpenCvNatives.ensureLoaded { check(OpenCVLoader.initLocal()) }
    @Test fun perspectiveTransformPreservesIdentityPixels() {
        load()
        val source=Mat(32,32,CvType.CV_8UC1)
        val result=Mat()
        val transform=Mat.eye(3,3,CvType.CV_64F)
        try {
            val bytes=ByteArray(32*32){(it%256).toByte()};source.put(0,0,bytes)
            Imgproc.warpPerspective(source,result,transform,Size(32.0,32.0))
            val actual=ByteArray(bytes.size);result.get(0,0,actual)
            assertArrayEquals(bytes,actual)
        } finally { source.release();result.release();transform.release() }
    }
    @Test fun productionImporterReadsSyntheticPhotograph() {
        load()
        val test=InstrumentationRegistry.getInstrumentation()
        val bytes=test.context.assets.open("scan-fixture.png").use { it.readBytes() }
        val outcome=PhotoReading.read(test.targetContext,bytes,0)
        assertTrue("Actual importer refused synthetic grid: $outcome",outcome is PhotoOutcome.Read)
        val state=(outcome as PhotoOutcome.Read).state
        assertEquals(30,state.grid.cells.count { it.source == org.freevia.sudokubuddy.model.CellSource.GIVEN })
    }
    @Test fun exportDecodedPhotographForParity() {
        val name=InstrumentationRegistry.getArguments().getString("probePhoto")
        assumeTrue("Supply probePhoto for local raw-pixel comparison",name!=null)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.filesDir,"ParityCorpus")
        val file=File(File(root,"corpus"),name!!)
        val orientation=ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)
        val rotation=when(orientation){6->90;3->180;8->270;else->0}
        val photo=Images.photograph(file.readBytes(),rotation)
        val out=File(root,"probe").apply { mkdirs() }
        File(out,"source.rgb").writeBytes(photo.rgb.pixels)
        File(out,"source.size").writeText("${photo.rgb.width} ${photo.rgb.height}\n")
        load()
        val full=Mat(photo.gray.height,photo.gray.width,CvType.CV_8UC1).apply { put(0,0,photo.gray.pixels) }
        try {
            for(edge in listOf(1000.0,1600.0)) {
                val scale=edge/maxOf(photo.gray.width,photo.gray.height)
                val small=Mat();val blur=Mat();val binary=Mat();val closed=Mat()
                val kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,Size(3.0,3.0))
                try {
                    Imgproc.resize(full,small,Size(photo.gray.width*scale,photo.gray.height*scale),0.0,0.0,Imgproc.INTER_LINEAR_EXACT)
                    Imgproc.GaussianBlur(small,blur,Size(5.0,5.0),0.0)
                    Imgproc.adaptiveThreshold(blur,binary,255.0,Imgproc.ADAPTIVE_THRESH_MEAN_C,Imgproc.THRESH_BINARY_INV,31,7.0)
                    Imgproc.morphologyEx(binary,closed,Imgproc.MORPH_CLOSE,kernel)
                    for((name,mat) in listOf("small" to small,"blur" to blur,"binary" to binary,"closed" to closed))
                        File(out,"android-${edge.toInt()}-$name.gray").writeBytes(ByteArray(mat.rows()*mat.cols()).also { mat.get(0,0,it) })
                } finally { listOf(small,blur,binary,closed,kernel).forEach { it.release() } }
            }
        } finally { full.release() }
        val verdict=org.freevia.sudokubuddy.vision.StructuralGate.assess(photo.gray)
        check(verdict is org.freevia.sudokubuddy.vision.GateVerdict.Usable)
        File(out,"android.gray").writeBytes(verdict.rectified.pixels)
        if(!verdict.curved) File(out,"rectified.rgb").writeBytes(org.freevia.sudokubuddy.vision.PerspectivePixels.rgb(photo.rgb,verdict.quad,1152).pixels)
        File(out,"lines.json").writeText(JSONObject().put("x",JSONArray(verdict.geometry.verticalLines)).put("y",JSONArray(verdict.geometry.horizontalLines)).toString())
        val reading=org.freevia.sudokubuddy.recognize.GridReader().read(verdict.cells,org.freevia.sudokubuddy.vision.ColorCells.extract(photo.rgb,verdict))
        val reports=when(reading){is org.freevia.sudokubuddy.recognize.ReadResult.Accepted->reading.readings;is org.freevia.sudokubuddy.recognize.ReadResult.NeedsConfirmation->reading.readings;else->emptyList()}
        File(out,"readings.json").writeText(JSONArray(reports.map { r->JSONObject().put("cell",r.index).put("ink",r.ink.name).put("height",r.heightRatio).put("darkness",r.darkness).put("probabilities",r.probabilities?.let { JSONArray(it.toList()) }) }).toString())
        File(out,"android.json").writeText(JSONObject().put("corners",JSONArray(verdict.quad.corners.map { JSONArray(listOf(it.x,it.y)) }))
            .put("curved",verdict.curved).put("score",verdict.gridScore).toString())
    }
    @Test fun replayFullLocalCorpusThroughProductionImporter() {
        val test=InstrumentationRegistry.getInstrumentation()
        val root=File(test.targetContext.filesDir,"ParityCorpus")
        assumeTrue("Stage local corpus before this test",File(root,"corpus").isDirectory)
        load()
        val output=File(root,"results.jsonl")
        output.bufferedWriter().use { writer ->
            for(folder in listOf("corpus","corpus-rectified")) for(file in File(root,folder).listFiles().orEmpty().sortedBy { it.name }) {
                if(file.name.startsWith(".") || file.extension.lowercase() !in setOf("jpg","jpeg","png","webp"))continue
                val at=System.nanoTime()
                val row=JSONObject().put("folder",folder).put("photo",file.name)
                try {
                    val orientation=runCatching { ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                    val rotation=when(orientation){ExifInterface.ORIENTATION_ROTATE_90->90;ExifInterface.ORIENTATION_ROTATE_180->180;ExifInterface.ORIENTATION_ROTATE_270->270;else->0}
                    check(orientation !in setOf(2,4,5,7)){"Mirrored EXIF needs explicit normalization"}
                    when(val result=PhotoReading.read(test.targetContext,file.readBytes(),rotation)) {
                        is PhotoOutcome.Refused -> row.put("status","refused").put("error",result.message)
                        is PhotoOutcome.Read -> {
                            val state=result.state
                            row.put("status","review").put("givens",JSONArray(state.grid.cells.map { if(it.source==org.freevia.sudokubuddy.model.CellSource.GIVEN)it.digit?:0 else 0 }))
                                .put("progress",JSONArray(state.grid.cells.map { if(it.source==org.freevia.sudokubuddy.model.CellSource.GUESS)it.digit?:0 else 0 }))
                                .put("uncertain",JSONArray(state.uncertainCells.sorted()))
                        }
                    }
                } catch(error:Exception){row.put("status","refused").put("error",error.toString())}
                row.put("seconds",(System.nanoTime()-at)/1e9)
                writer.appendLine(row.toString());writer.flush()
            }
        }
        File(root,"complete").writeText("complete")
    }
}
