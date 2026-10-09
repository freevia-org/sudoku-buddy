package org.freevia.sudokubuddy.vision

import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Replays quality changes at fixed corpus geometry; this is not a live camera benchmark. */
class CaptureQualityCorpusTest {
    private fun blurred(image: GrayImage): GrayImage {
        val input = image.toMat()
        val output = Mat()
        return try {
            Imgproc.GaussianBlur(input, output, Size(19.0, 19.0), 0.0)
            output.toGrayImage()
        } finally {
            input.release()
            output.release()
        }
    }

    @Test
    fun `corpus stationary replay defers capture during introduced focus regression`() {
        if (System.getProperty("dump") != "true") return
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        var found = 0
        var eligible = 0
        var wholeBlurDeferred = 0
        var partialBlurDeferred = 0
        for (file in CorpusFixtures.photos) {
            val frame = CorpusFixtures.load(file)
            val located = GridLocator.locate(frame) as? GridLocation.Found ?: continue
            found++
            val quality = ImageQuality.of(located.rectified)
            val advisor = FramingAdvisor()
            var ready = false
            repeat(7) { ready = advisor.adviseLocated(frame, located.quad, quality).readyToCapture }
            if (!ready) continue
            eligible++
            val blur = blurred(located.rectified)
            val blurredQuality = ImageQuality.of(blur)
            assertTrue(blurredQuality.sharpness < quality.sharpness * 0.85, file.name)
            assertFalse(advisor.adviseLocated(frame, located.quad, blurredQuality).readyToCapture, file.name)
            wholeBlurDeferred++
            assertTrue(advisor.adviseLocated(frame, located.quad, quality).readyToCapture, file.name)
            val partialPixels = located.rectified.pixels.copyOf()
            val width = located.rectified.width
            for (y in 0 until located.rectified.height / 2) {
                System.arraycopy(blur.pixels, y * width, partialPixels, y * width, width / 2)
            }
            val partialQuality = ImageQuality.of(GrayImage(width, located.rectified.height, partialPixels))
            assertFalse(advisor.adviseLocated(frame, located.quad, partialQuality).readyToCapture, file.name)
            partialBlurDeferred++
            assertTrue(advisor.adviseLocated(frame, located.quad, quality).readyToCapture, file.name)
        }
        assertTrue(eligible > 0)
        println("CAPTURE_QUALITY_REPLAY photos=${CorpusFixtures.photos.size} grids=$found " +
            "framingEligible=$eligible wholeBlurDeferred=$wholeBlurDeferred " +
            "partialBlurDeferred=$partialBlurDeferred recovered=$eligible")
    }
}
