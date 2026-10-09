package org.freevia.sudokubuddy.vision

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the camera says when it cannot find a grid.
 *
 * Reported from the phone: a puzzle filling the frame, and the app answering "point the
 * camera at a sudoku puzzle" - which is advice to do the thing already being done. The
 * message is the only thing the user can act on, so each failure has to say something
 * different and true.
 *
 * Built from plain synthetic frames rather than the corpus, so these run anywhere.
 */
class FramingAdvisorTest {

    @BeforeTest
    fun setUp() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
    }

    private fun flat(shade: Int, width: Int = 480, height: Int = 360) =
        GrayImage(width, height, ByteArray(width * height) { shade.toByte() })

    /** A filled rectangle on a light ground: something square-cornered, but not a grid. */
    private fun blankSquare(): GrayImage {
        val image = flat(230)
        for (y in 60 until 300) {
            for (x in 120 until 360) {
                val edge = y < 66 || y >= 294 || x < 126 || x >= 354
                image.pixels[y * image.width + x] = (if (edge) 20 else 245).toByte()
            }
        }
        return image
    }

    private fun advise(image: GrayImage) = FramingAdvisor().advise(image)

    @Test
    fun `a dark frame says so rather than blaming the aim`() {
        val guidance = advise(flat(20))
        assertEquals("Too dark to make out the grid lines", guidance.message)
        assertFalse(guidance.readyToCapture)
    }

    @Test
    fun `a blown-out frame blames the light rather than the aim`() {
        assertEquals(
            "Glare is washing the lines out - tilt the page away from the light",
            advise(flat(255)).message,
        )
    }

    /** Nothing square-cornered anywhere: the original advice is the right advice. */
    @Test
    fun `an empty scene still asks for a puzzle`() {
        assertEquals("Point the camera at a sudoku puzzle", advise(flat(140)).message)
    }

    /**
     * The case that prompted all this: something is plainly in frame, well lit, and square
     * - it just does not read as nine rows and nine columns. Saying "point the camera at a
     * sudoku puzzle" there is the one answer that cannot be acted on.
     */
    @Test
    fun `a square that is not a grid says what is wrong with it`() {
        val message = advise(blankSquare()).message
        assertTrue(
            message.contains("nine by nine") || message.startsWith("Almost"),
            "expected an explanation of what does not read as a grid, but got: $message",
        )
    }

    private fun centeredQuad(scale: Double = 1.0, dx: Double = 0.0) = Quad(
        Corner((120.0 + dx) * scale, 180.0 * scale),
        Corner((600.0 + dx) * scale, 180.0 * scale),
        Corner((600.0 + dx) * scale, 660.0 * scale),
        Corner((120.0 + dx) * scale, 660.0 * scale),
    )

    private fun quality(sharpness: Double = 100.0, ratio: Double = 0.7) =
        ImageQuality(sharpness, 200.0, 0.0, ratio)

    @Test
    fun `auto capture waits through whole grid and partial focus regression`() {
        val advisor = FramingAdvisor(stableFramesRequired = 2)
        val frame = flat(200, 720, 960)
        val quad = centeredQuad()
        advisor.adviseLocated(frame, quad, quality())
        advisor.adviseLocated(frame, quad, quality())
        assertFalse(advisor.adviseLocated(frame, quad, quality(40.0, ratio = 1.0)).readyToCapture)
        assertFalse(advisor.adviseLocated(frame, quad, quality(ratio = 0.3)).readyToCapture)
        assertTrue(advisor.adviseLocated(frame, quad, quality()).readyToCapture)
    }

    @Test
    fun `motion tolerance behaves the same at different analysis resolutions`() {
        for (scale in listOf(0.5, 1.0, 2.0)) {
            val frame = flat(200, (720 * scale).toInt(), (960 * scale).toInt())
            val advisor = FramingAdvisor(stableFramesRequired = 1)
            advisor.adviseLocated(frame, centeredQuad(scale), quality())
            assertTrue(advisor.adviseLocated(frame, centeredQuad(scale, 10.0), quality()).readyToCapture)
            assertFalse(advisor.adviseLocated(frame, centeredQuad(scale, 25.0), quality()).readyToCapture)
        }
    }

    @Test
    fun `focus history expires and resets on movement instead of locking shutter indefinitely`() {
        val advisor = FramingAdvisor(stableFramesRequired = 2)
        val frame = flat(200, 720, 960)
        val quad = centeredQuad()
        repeat(3) { advisor.adviseLocated(frame, quad, quality()) }
        assertFalse(advisor.adviseLocated(frame, quad, quality(40.0)).readyToCapture)
        repeat(8) { advisor.adviseLocated(frame, quad, quality(40.0)) }
        assertTrue(advisor.adviseLocated(frame, quad, quality(40.0)).readyToCapture)
        advisor.reset()
        assertFalse(advisor.adviseLocated(frame, quad, quality(40.0)).readyToCapture)
    }
}
