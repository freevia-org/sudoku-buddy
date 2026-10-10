package org.freevia.sudokubuddy.app

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun MisreadSubmissionDialog(
    autoShare: Boolean,
    trainingConsent: Boolean,
    trainingConsentLocked: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Boolean, Boolean) -> Unit,
) {
    var shareAutomatically by remember { mutableStateOf(autoShare) }
    var trainThisReport by remember { mutableStateOf(trainingConsent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit uncertain reading for analysis") },
        text = {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Thanks for helping us improve recognition. Your report shares the " +
                        "puzzle photo, reading results and cell corrections with Freevia; " +
                        "later corrections can be added when you submit an update or enable " +
                        "automatic sharing."
                )
                if (trainingConsentLocked) {
                    Text(
                        if (trainingConsent) {
                            "This reading was opted in to private training. You can request " +
                                "deletion from Submission receipts. A model already trained " +
                                "on it may not be retroactively unlearnable. Later corrections " +
                                "keep this reading's choice."
                        } else {
                            "This reading is analysis-only. Later corrections keep this choice."
                        },
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = trainThisReport, onCheckedChange = {
                            trainThisReport = it
                        })
                        Text(
                            "Keep this report as a private training example",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        if (trainThisReport) {
                            "Training copies are retained until you request deletion from " +
                                "Submission receipts. If already used to train a model, its " +
                                "learned changes may remain."
                        } else {
                            "If you later opt in, training copies are retained until you " +
                                "request deletion from Submission receipts. A model already " +
                                "trained may retain learned changes."
                        },
                        modifier = Modifier.padding(start = 48.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!MisreadSubmission.available) {
                    Text(
                        "Submissions are not available in this build.",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = shareAutomatically, onCheckedChange = {
                        shareAutomatically = it
                    })
                    Text(
                        "Also auto-share future uncertain puzzle photos, readings and corrections",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    if (trainingConsentLocked && trainingConsent ||
                        !trainingConsentLocked && trainThisReport) {
                        "Training copies can be deleted by receipt. Turn off future auto-sharing " +
                            "in Settings; trained models may retain learned changes."
                    } else {
                        "Analysis copies are kept up to 90 days. Turn off future auto-sharing " +
                            "in Settings."
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    enabled = MisreadSubmission.available,
                    onClick = { onSubmit(shareAutomatically, trainThisReport) },
                ) { Text("Submit") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun SubmissionReceiptsDialog(
    receipts: List<SubmissionReceipt>,
    onDelete: (SubmissionReceipt) -> Unit,
    deletingReceipt: String?,
    deleteError: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<SubmissionReceipt?>(null) }
    LaunchedEffect(receipts, pendingDelete) {
        if (pendingDelete != null && receipts.none { it.digest == pendingDelete?.digest }) {
            pendingDelete = null
        }
    }
    val request = "Please delete my Sudoku Buddy recognition submissions. Receipt IDs: " +
        receipts.joinToString(", ") { it.digest }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submission receipts") },
        text = {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                Text("Each receipt identifies one submitted puzzle revision. Delete it here " +
                    "to remove the report and any private training copy from Freevia.")
                receipts.forEach { receipt ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(receipt.digest, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f))
                        TextButton(
                            enabled = deletingReceipt == null && MisreadSubmission.available,
                            onClick = { pendingDelete = receipt },
                        ) { Text(if (deletingReceipt == receipt.digest) "Deleting…" else "Delete") }
                    }
                }
                deleteError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                (context.getSystemService(ClipboardManager::class.java) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Deletion request", request))
                onDismiss()
            }) {
                Text("Copy deletion request")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    pendingDelete?.let { receipt ->
        AlertDialog(
            onDismissRequest = { if (deletingReceipt == null) pendingDelete = null },
            title = { Text("Delete this submission?") },
            text = {
                Column {
                    Text("Freevia will delete this report revision and its private training " +
                        "copy. Future corrections may create a new report under your sharing " +
                        "settings. A model already trained on it may retain learned changes.")
                    deleteError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = deletingReceipt == null,
                    onClick = { onDelete(receipt) }) {
                    Text(if (deletingReceipt == receipt.digest) "Deleting…" else "Delete report")
                }
            },
            dismissButton = {
                TextButton(enabled = deletingReceipt == null, onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
