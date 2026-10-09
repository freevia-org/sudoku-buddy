package org.freevia.sudokubuddy.app

import android.content.Context
import androidx.core.content.edit

/** App-wide local copy of upload receipts, independent of a puzzle's history entry. */
internal object SubmissionReceiptStore {
    private const val FILE = "sudokubuddy.submission-receipts"
    private const val KEY = "receipts"

    @Synchronized
    fun list(context: Context): List<SubmissionReceipt> = context
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)
        .getString(KEY, "").orEmpty()
        .lineSequence()
        .filter(String::isNotBlank)
        .mapNotNull { line ->
            runCatching {
                val parts = line.split(',')
                require(parts.size == 3)
                SubmissionReceipt(parts[0], parts[1], parts[2].toLong())
            }.getOrNull()
        }
        .distinctBy(SubmissionReceipt::digest)
        .toList()

    @Synchronized
    fun add(context: Context, receipt: SubmissionReceipt) {
        val receipts = list(context).let { existing ->
            if (existing.any { it.digest == receipt.digest }) existing else existing + receipt
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            putString(KEY, receipts.joinToString("\n") {
                "${it.digest},${it.status},${it.receivedAtMillis}"
            })
        }
    }
}
