package org.freevia.sudokubuddy.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.freevia.sudokubuddy.BuildConfig

@Composable
internal fun GitHubFeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report a bug or idea on GitHub") },
        text = {
            Text(
                "Open a draft in your browser with the app version, phone model, Android " +
                    "version and recent scan diagnostics filled in. Add a full description " +
                    "and relevant screenshots yourself.\n\n" +
                    "GitHub sign-in is required. You can review and edit the draft before " +
                    "submitting it. Submitted reports and attachments are public. No " +
                    "photographs are attached automatically.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                val opened = runCatching {
                    val url = FeedbackDraft.issueUrl(
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                        device = "${Build.MANUFACTURER} ${Build.MODEL}",
                        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        diagnostics = runCatching { Diagnostics.report(context) }
                            .getOrDefault("Scan diagnostics could not be read."),
                    )
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.isSuccess
                if (!opened) {
                    Toast.makeText(
                        context,
                        "Could not open GitHub. You can email info@freevia.org for support.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }) { Text("Open GitHub") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
