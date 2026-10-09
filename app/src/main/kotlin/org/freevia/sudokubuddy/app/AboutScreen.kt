package org.freevia.sudokubuddy.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.freevia.sudokubuddy.BuildConfig

/**
 * What the app is, what it does with your photographs, and what it is built on.
 *
 * Reached from its own button in the bar rather than through settings: none of it is a
 * setting, and a page of prose behind a row of switches is a page nobody reads twice.
 */
@Composable
fun AboutScreen(onClose: () -> Unit) {
    var licensesOpen by remember { mutableStateOf(false) }
    if (licensesOpen) {
        LicenseScreen(onClose = { licensesOpen = false })
        return
    }
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(title = "About", subtitle = "Sudoku Buddy ${BuildConfig.VERSION_NAME}", onBack = onClose)
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Section("What it does") {
                    Text(
                        "Point the camera at a sudoku puzzle on paper. The app reads the " +
                            "printed digits and anything you have written in, solves it, and " +
                            "can give you a hint, check your answers, or show the whole " +
                            "solution.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Recognition can make mistakes, especially with handwriting. " +
                            "Use Read to compare the recognised digits with the photo, and " +
                            "tap any square to correct it. The app marks uncertain readings " +
                            "for review; a confident reading can still be wrong.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Section("Your photos and your privacy") {
                    Text(
                        "Puzzles are read and solved on this phone. If you submit an uncertain " +
                            "reading, or enable automatic sharing, the puzzle photo, original " +
                            "readings, uncertainty flags and your corrections are sent securely " +
                            "to Freevia for recognition analysis. Reports do not include your " +
                            "device model or diagnostics. Automatic sharing is optional; there " +
                            "are no accounts, routine usage analytics or ad tracking. " +
                            "Submitted reports are analyzed only to improve recognition and are kept for up " +
                            "to 90 days; use Submission receipts in Settings to copy a deletion request.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Photographs you keep are stored in this app's private storage and are " +
                            "removed when you delete them or uninstall the app.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Sudoku Buddy is published by Freevia. Contact: info@freevia.org",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = {
                        uriHandler.openUri("https://freevia.org/sudoku-buddy/privacy")
                    }) { Text("Privacy policy") }
                }
            }

            item { HorizontalDivider() }

            item {
                Section("Open source notices") {
                    Text("Sudoku Buddy source code is licensed under Apache License 2.0. " +
                        "Freevia branding is subject to separate trademark rights.")
                    TextButton(onClick = { licensesOpen = true }) { Text("Licenses") }
                    Text(
                        "OpenCV - Apache License 2.0. Used for finding and straightening the " +
                            "grid.\n\n" +
                            "AndroidX, Jetpack Compose and CameraX - Apache License 2.0.\n\n" +
                            "Kotlin - Apache License 2.0.\n\n" +
                            "The digit recogniser was trained on the MNIST database of " +
                            "handwritten digits by LeCun, Cortes and Burges, together with " +
                            "digits rendered from open system fonts and digits drawn " +
                            "programmatically.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Section("Puzzles") {
                    Text(
                        "Sudoku is a public-domain puzzle form. This app solves puzzles you " +
                            "already have; it does not reproduce or distribute anyone's " +
                            "puzzles.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Puzzles photographed for testing came from sudoku.cba.si. This app is " +
                            "not affiliated with them.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
