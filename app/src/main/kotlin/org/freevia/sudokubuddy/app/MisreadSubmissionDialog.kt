package org.freevia.sudokubuddy.app

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun MisreadSubmissionDialog(
    autoShare: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Boolean) -> Unit,
) {
    var shareAutomatically by remember { mutableStateOf(autoShare) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit uncertain reading for analysis") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Thanks for helping improve recognition. Your puzzle photo, readings and " +
                        "corrections will be used for analysis and training."
                )
                ConsentOption(
                    checked = shareAutomatically,
                    onCheckedChange = { shareAutomatically = it },
                    title = "Submit future uncertain readings automatically",
                    detail = "Includes their photos, readings and corrections. Turn this off in Settings.",
                )
                if (!MisreadSubmission.available) {
                    Text(
                        "Submissions are not available in this build.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = MisreadSubmission.available,
                onClick = { onSubmit(shareAutomatically) },
            ) { Text("Submit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ConsentOption(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
