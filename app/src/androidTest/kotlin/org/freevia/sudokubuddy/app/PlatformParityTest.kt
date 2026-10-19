package org.freevia.sudokubuddy.app

import android.content.ContentValues
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.freevia.sudokubuddy.model.Grid
import org.junit.Rule
import org.junit.Test

/** Screenshots and taps use the production Compose screens with the same native fixture as iOS. */
class PlatformParityTest {
    @get:Rule val ui = createComposeRule()
    private val clues = "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"
    private fun puzzle(): PuzzleState {
        // This synthetic fixture is already square. Crop its white border for a UI-only
        // comparison; the production reader is covered separately by corpus tests.
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("scan-fixture.png").use { it.readBytes() }
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        var left = source.width; var top = source.height; var right = 0; var bottom = 0
        for (y in 0 until source.height) for (x in 0 until source.width) {
            if (Color.red(source.getPixel(x, y)) < 64) {
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        val photo = Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
        return PuzzleState(photo, Grid.fromGivens(clues), emptySet())
    }
    private fun capture(name: String) {
        ui.waitForIdle()
        val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SudokuBuddyParity")
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        checkNotNull(resolver.openOutputStream(uri)).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun fourHintStagesAndProofNavigation() {
        var state by mutableStateOf(puzzle())
        ui.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    PuzzleScreen(state, { state = it }, {}, {}, {}, {}, {}, false, false, {}, null, null, { _, _ -> })
                }
            }
        }
        capture("puzzle-none")
        for (depth in 0..3) {
            ui.onNodeWithText("Hint", useUnmergedTree = true).performClick()
            ui.onNodeWithText("${depth + 1}. ${listOf("Where", "Square", "Trace", "Answer")[depth]}").assertExists()
            capture("hint-$depth")
        }
        ui.onNodeWithText("Why this square? · Naked single").performScrollTo().performClick()
        ui.onNodeWithText("Before").assertExists()
        capture("hint-placement-proof")
        ui.onNodeWithText("Back to your square").performScrollTo().performClick()
        ui.onNodeWithText("Close hint").performScrollTo().performClick()
        ui.onNodeWithText("1. Where").assertDoesNotExist()
    }
    @Test fun auxiliaryScreensAndTutor() {
        var screen by mutableStateOf("settings")
        var state by mutableStateOf(puzzle().tutor())
        ui.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    when (screen) {
                        "settings" -> SettingsScreen(Settings(), emptyList(), {}, null, null, {}, {})
                        "about" -> AboutScreen({})
                        "strategies" -> StrategiesScreen(emptyMap(), {}, {})
                        "history" -> HistoryList(emptyList(), null, {}, {}, {}, {})
                        "consent" -> MisreadSubmissionDialog(false, {}, {})
                        else -> PuzzleScreen(state, { state = it }, {}, {}, {}, {}, {}, false, false, {}, null, null, { _, _ -> })
                    }
                }
            }
        }
        for (name in listOf("settings", "about", "strategies", "history", "consent", "tutor")) {
            ui.runOnIdle { screen = name }
            capture(name)
        }
        ui.runOnIdle { state = state.stepTo(1) }
        capture("tutor-first-step")
    }
}
