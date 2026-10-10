package org.freevia.sudokubuddy.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.freevia.sudokubuddy.solver.RouteStyle

/**
 * The few things worth choosing.
 *
 * About, privacy and the licences used to sit at the bottom of this page, then behind a
 * row on it. They are reading rather than settings and now have their own button in the
 * bar, so there is nothing here but the switches.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    submissionReceipts: List<SubmissionReceipt>,
    onChange: (Settings) -> Unit,
    onClose: () -> Unit,
) {
    var receiptsOpen by remember { mutableStateOf(false) }
    var confirmAutoShare by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(title = "Settings", onBack = onClose)
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SettingRow(
                    title = "Explain hints",
                    detail = "Name the technique and show the squares that prove it, instead " +
                        "of just giving the digit.",
                    checked = settings.hintStyle == HintStyle.EXPLAIN,
                    onChange = {
                        onChange(
                            settings.copy(
                                hintStyle = if (it) HintStyle.EXPLAIN else HintStyle.REVEAL
                            )
                        )
                    },
                )
            }

            item {
                // Not a better-and-worse but a genuine trade, and the numbers are from the
                // hardest puzzle in the test set, measured both ways.
                SettingRow(
                    title = "Keep forcing chains short",
                    detail = "Compare forcing chains and prefer ones with fewer squares to " +
                        "follow. Shorter chains can mean more steps in the full solution.",
                    checked = settings.routeStyle == RouteStyle.SHORT_CHAINS,
                    onChange = {
                        onChange(
                            settings.copy(
                                routeStyle = if (it) {
                                    RouteStyle.SHORT_CHAINS
                                } else {
                                    RouteStyle.FIRST_FOUND
                                }
                            )
                        )
                    },
                )
            }

            item {
                SettingRow(
                    title = "Take the photo automatically",
                    detail = "Fire the shutter once the grid is square in frame and steady. " +
                        "The button always works either way.",
                    checked = settings.autoCapture,
                    onChange = { onChange(settings.copy(autoCapture = it)) },
                )
            }

            item {
                SettingRow(
                    title = "Share automatically when uncertain",
                    detail = "Automatically share uncertain puzzle photos, reading results and " +
                        "later cell corrections with Freevia for recognition analysis. Off by default.",
                    checked = settings.autoShareWhenUncertain,
                    onChange = {
                        if (it) confirmAutoShare = true
                        else onChange(settings.copy(autoShareWhenUncertain = false))
                    },
                )
            }

            if (submissionReceipts.isNotEmpty()) {
                item {
                    androidx.compose.material3.TextButton(onClick = { receiptsOpen = true }) {
                        Text("Submission receipts (${submissionReceipts.size})")
                    }
                }
            }
        }
    }
    if (confirmAutoShare) {
        AlertDialog(
            onDismissRequest = { confirmAutoShare = false },
            title = { Text("Share uncertain readings automatically?") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Future uncertain readings will send the straightened puzzle photo, " +
                            "recognition results and corrections to Freevia for private analysis. " +
                            "If an uncertain puzzle is open when you turn this on, it will be sent " +
                            "immediately too. Reports may be kept for up to 90 days, and later " +
                            "corrections are shared while this setting is on. You can turn it off " +
                            "in Settings."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAutoShare = false
                    onChange(settings.copy(autoShareWhenUncertain = true))
                }) { Text("Turn on") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAutoShare = false }) { Text("Cancel") }
            },
        )
    }
    if (receiptsOpen) SubmissionReceiptsDialog(submissionReceipts) { receiptsOpen = false }
}

@Composable
private fun SettingRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
