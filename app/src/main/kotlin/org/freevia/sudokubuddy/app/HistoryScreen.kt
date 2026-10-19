package org.freevia.sudokubuddy.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every puzzle read so far, newest first, under day headings.
 *
 * Lives in the navigation drawer rather than on a screen of its own: it is a list you
 * reach for while looking at a puzzle, and sending the user somewhere else to pick one
 * only to send them straight back is a longer way round to the same place.
 *
 * Grouped by day because the list only grows, and a date is how anyone actually
 * remembers which puzzle they want.
 */
@Composable
fun HistoryList(
    entries: List<HistoryEntry>,
    currentId: Long?,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onCamera: () -> Unit,
    onClose: () -> Unit,
    /** Photographs the app would not read, newest first. */
    refused: List<Diagnostics.Refused> = emptyList(),
    onDiscard: (Diagnostics.Refused) -> Unit = {},
) {
    var pendingDelete by remember { mutableStateOf<HistoryEntry?>(null) }

    // Photographs the app would not read at all. They never became puzzles, so nothing
    // else in the app knows they happened - and they are exactly the ones worth sending
    // to somebody who can find out why.
    //
    // Handed in and refreshed when the drawer opens, the same way the puzzles are. Reading
    // the folder once inside this composable is not enough: the drawer's contents stay
    // composed between openings, so a scan that failed afterwards never appeared - which
    // looks exactly like the photograph never having been kept.
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    LazyColumn(
        // No inset padding here: ModalDrawerSheet already applies the system bars, and
        // adding them again pushes the first entry a status bar's height down the sheet.
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // A way out that does not depend on finding the scrim. The sheet is as wide as
        // a small phone's whole screen, so there may be no scrim to tap at all.
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    localized("Your puzzles"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = localized("Close"))
                }
            }
        }

        // The way to a new puzzle, above the old ones. The camera also has its own icon
        // in the bar, but this is the menu you open when the question is "which puzzle" -
        // and "a new one" is one of the answers to that question.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCamera)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CameraIcon, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(localized("New sudoku"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        localized("Photograph another puzzle."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    localized("Puzzles you photograph are kept here, so you can pick one up again later."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

        for ((day, group) in History.byDay(entries, locale)) {
            item {
                Text(
                    localized(day),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            for (entry in group) {
                item(key = entry.id) {
                    val summary by produceState("${entry.grid.givenCount} printed", entry.grid) {
                        value = withContext(Dispatchers.Default) { summarise(entry) }
                    }
                    HistoryRowContent(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(entry) }.padding(vertical = 6.dp),
                        thumbnail = {
                            Box(modifier = Modifier.size(52.dp)) {
                                val thumbnail by produceState<android.graphics.Bitmap?>(null, entry.photo) {
                                    value = withContext(Dispatchers.IO) {
                                        runCatching { BitmapFactory.decodeFile(entry.photo.absolutePath,
                                            BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull()
                                    }
                                }
                                thumbnail?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        },
                        heading = {
                            Text(
                                localized(History.timeOf(entry, locale)),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (entry.id == currentId) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        description = { Text(localized(summary), style = MaterialTheme.typography.bodySmall) },
                        actions = {
                            HistoryActions(
                                onSend = { Diagnostics.share(context, listOf(entry.photo)) },
                                onDelete = { pendingDelete = entry },
                                deleteLabel = "Delete this puzzle",
                            )
                        },
                    )
                }
            }
        }

        if (refused.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                    Text(
                        localized("Would not read"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        localized("Photos the app could not make a puzzle out of. Send one and it " +
                            "can be looked at."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            for (scan in refused) {
                item(key = scan.file.name) {
                    RefusedRow(
                        scan = scan,
                        onSend = { Diagnostics.share(context, listOf(scan.file)) },
                        onDelete = { onDiscard(scan) },
                    )
                }
            }
        }

        item { Box(Modifier.size(24.dp)) }
    }

    // Deleting throws away the photograph as well as the grid, and there is no undo, so
    // it is worth one tap to be sure.
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text(localized("Delete this puzzle?")) },
            text = {
                Text(
                    localized("The photo from ${History.timeOf(entry, locale)} and everything read from it " +
                        "will be removed from this phone. This cannot be undone.")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(entry)
                    pendingDelete = null
                }) {
                    Text(localized("Delete"), color = Overlays.incorrect)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(localized("Keep")) }
            },
        )
    }
}

/**
 * One photograph the app turned down, with its reason.
 *
 * Deliberately looks like a puzzle row and is deliberately not one: there is no puzzle to
 * open, so tapping it does nothing. The thumbnail is the point - it shows at a glance
 * whether the camera was even pointed at the right thing.
 */
@Composable
private fun RefusedRow(
    scan: Diagnostics.Refused,
    onSend: () -> Unit,
    onDelete: () -> Unit,
) {
    HistoryRowContent(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        thumbnail = {
            Box(modifier = Modifier.size(52.dp)) {
                val thumbnail by produceState<android.graphics.Bitmap?>(null, scan.file) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { BitmapFactory.decodeFile(scan.file.absolutePath, thumbnailOptions()) }
                            .getOrNull()
                    }
                }
                thumbnail?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        },
        heading = {
            Text(
                localized(History.timeOf(scan.at, LocalConfiguration.current.locales[0])),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        description = { Text(localized(scan.reason), style = MaterialTheme.typography.bodySmall) },
        actions = { HistoryActions(onSend, onDelete, "Delete this photo") },
    )
}

@Composable
private fun HistoryRowContent(
    modifier: Modifier,
    thumbnail: @Composable () -> Unit,
    heading: @Composable () -> Unit,
    description: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                thumbnail()
                Box(Modifier.weight(1f)) { heading() }
                actions()
            }
            description()
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            thumbnail()
            Column(Modifier.weight(1f)) { heading(); description() }
            actions()
        }
    }
}

@Composable
private fun HistoryActions(onSend: () -> Unit, onDelete: () -> Unit, deleteLabel: String) {
    val actions: @Composable () -> Unit = {
        IconButton(onClick = onSend) {
            Icon(Icons.Filled.Share, contentDescription = localized("Send this photo"))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = localized(deleteLabel))
        }
    }
    if (LocalDensity.current.fontScale > 1.3f) Column { actions() } else Row { actions() }
}

/**
 * A capture is several thousand pixels across and a thumbnail is fifty-two.
 *
 * Decoding one at full size to draw it that small is tens of megabytes for nothing, and
 * with several in a list it is the difference between a drawer that opens and one that
 * stutters.
 */
private fun thumbnailOptions() = BitmapFactory.Options().apply { inSampleSize = 16 }

/** A line telling you which puzzle this is without opening it. */
private fun summarise(entry: HistoryEntry): String = PuzzleLogic.historySummary(entry.grid)
