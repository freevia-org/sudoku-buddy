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
            Column {
                Text("Thank you for helping us improve recognition. Your report shares the " +
                    "puzzle photo and reading results, which we will use to improve the reading algorithm.")
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
                    Text("Share automatically when uncertain", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    enabled = MisreadSubmission.available,
                    onClick = { onSubmit(shareAutomatically) },
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
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val request = "Please delete my Sudoku Buddy recognition submissions. Receipt IDs: " +
        receipts.joinToString(", ") { it.digest }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submission receipts") },
        text = {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                Text("Each receipt identifies one submitted puzzle revision. Email " +
                    "info@freevia.org to request deletion.")
                receipts.forEach { receipt ->
                    Text(receipt.digest, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp))
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
}
