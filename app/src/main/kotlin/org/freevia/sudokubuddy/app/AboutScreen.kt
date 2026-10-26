package org.freevia.sudokubuddy.app

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
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
    val context = LocalContext.current
    val packageInfo = remember(context) { installedPackageInfo(context) }
    val versionName = packageInfo.versionName ?: BuildConfig.VERSION_NAME
    val buildNumber = PackageInfoCompat.getLongVersionCode(packageInfo)
    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(
            title = "About",
            subtitle = "Sudoku Buddy $versionName",
            onBack = onClose,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Section("Version") {
                    Text(
                        "$versionName (build $buildNumber)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                Section("What it does") {
                    Text(
                        localized("Point the camera at a sudoku puzzle on paper. The app reads the " +
                            "printed digits and anything you have written in, solves it, and " +
                            "can give you a hint, check your answers, or show the whole " +
                            "solution."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        localized("Recognition can make mistakes, especially with handwriting. " +
                            "Use Read to compare the recognised digits with the photo, and " +
                            "tap any square to correct it. The app marks uncertain readings " +
                            "for review; a confident reading can still be wrong."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Section("Your photos and your privacy") {
                    Text(
                        localized("Puzzles are read and solved on this phone. If you submit an uncertain " +
                        "reading, or enable automatic sharing, the puzzle photo, original " +
                            "readings, uncertainty flags and your corrections are sent securely " +
                            "to Freevia for recognition analysis and private training. Reports do not include your " +
                            "device model or diagnostics. Automatic sharing is optional; there " +
                            "are no accounts, routine usage analytics or ad tracking. Training " +
                            "copies can be deleted by receipt, though a " +
                            "model already trained may retain learned changes. Use Submission " +
                            "receipts in Settings to delete a report."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        localized("Photographs you keep are stored in this app's private storage and are " +
                            "removed when you delete them or uninstall the app."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        localized("Sudoku Buddy is published by Freevia. Contact: info@freevia.org"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = {
                        uriHandler.openUri("https://freevia.org/sudoku-buddy/privacy")
                    }) { Text(localized("Privacy policy")) }
                }
            }

            item { HorizontalDivider() }

            item {
                Section("Open source notices") {
                    Text(localized("Sudoku Buddy source code is licensed under Apache License 2.0. " +
                        "Freevia branding is subject to separate trademark rights."))
                    TextButton(onClick = { licensesOpen = true }) { Text(localized("Licenses")) }
                    Text(
                        localized("OpenCV - Apache License 2.0. Used for finding and straightening the " +
                            "grid.\n\n" +
                            "AndroidX, Jetpack Compose and CameraX - Apache License 2.0.\n\n" +
                            "Kotlin - Apache License 2.0.\n\n" +
                            "The digit recogniser was trained on the MNIST database of " +
                            "handwritten digits by LeCun, Cortes and Burges, together with " +
                            "digits rendered from open system fonts and digits drawn " +
                            "programmatically."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Section("Puzzles") {
                    Text(
                        localized("Sudoku is a public-domain puzzle form. This app solves puzzles you " +
                            "already have; it does not reproduce or distribute anyone's " +
                            "puzzles."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        localized("Puzzles photographed for testing came from sudoku.cba.si. This app is " +
                            "not affiliated with them."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun installedPackageInfo(context: Context): PackageInfo =
    if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(localized(title), style = MaterialTheme.typography.titleMedium)
        content()
    }
}
