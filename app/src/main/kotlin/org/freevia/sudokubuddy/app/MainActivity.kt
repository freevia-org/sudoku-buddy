package org.freevia.sudokubuddy.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.freevia.sudokubuddy.vision.OpenCvNatives
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the status and navigation bars, and let each screen inset its own
        // content. The camera wants the preview under them; nothing else does.
        //
        // The bars are pinned dark rather than left on auto: this app is dark whatever
        // the phone is set to, so on a phone in light mode `auto` would put dark system
        // icons on a dark background and they would vanish.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        // The vision module never links against a platform, so the loader is injected.
        OpenCvNatives.ensureLoaded {
            check(OpenCVLoader.initLocal()) { "OpenCV native libraries failed to load" }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val history = remember { History(context) }
    val scope = rememberCoroutineScope()
    val storage = remember { Mutex() }
    var storageBusy by remember { mutableStateOf(false) }
    var storageError by remember { mutableStateOf<String?>(null) }
    var puzzleGeneration by remember { mutableStateOf(0L) }

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    var settings by remember { mutableStateOf(Settings.load(context)) }
    var submissionReceipts by remember { mutableStateOf(SubmissionReceiptStore.list(context)) }
    var puzzle by remember { mutableStateOf<PuzzleState?>(null) }
    var submissionInFlight by remember { mutableStateOf(false) }
    val pendingAutoSubmissions = remember { AutoSubmissionQueue<PuzzleState>() }
    var entries by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    // Photographs the app refused. Looked up with the puzzles, since the drawer shows both.
    var refused by remember { mutableStateOf(emptyList<Diagnostics.Refused>()) }
    var nav by remember { mutableStateOf(Navigation(Screen.CAMERA)) }
    val screen = nav.screen

    // Which history entry the puzzle on screen belongs to, so corrections are written
    // back to it. Without this, reopening a puzzle undoes every fix the user made.
    var entryId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun restore(entry: HistoryEntry, photo: android.graphics.Bitmap): PuzzleState = PuzzleState(
        photo = photo, grid = entry.grid, uncertainCells = entry.details.uncertain,
        framingNote = entry.details.framingNote, readerComplaint = entry.details.readerComplaint,
        lines = entry.details.lines, entered = entry.details.entered, reports = entry.details.reports,
        originalGrid = entry.details.originalGrid ?: entry.grid,
        originalReports = entry.details.originalReports ?: entry.details.reports,
        originalUncertainCells = entry.details.originalUncertain.ifEmpty { entry.details.uncertain },
        readingCorrections = entry.details.corrections,
        submittedCorrectionCount = entry.details.submittedCorrectionCount,
        submissionReceipts = entry.details.receipts,
        hintStyle = settings.hintStyle, routeStyle = settings.routeStyle,
    )

    LaunchedEffect(history) {
        val generation = puzzleGeneration
        val restoreId = entryId
        val initialNavigation = nav
        val saved = withContext(Dispatchers.IO) { history.list() }
        entries = saved
        refused = withContext(Dispatchers.IO) { Diagnostics.refused(context) }
        saved.firstOrNull { it.id == restoreId }?.let { entry ->
            withContext(Dispatchers.IO) { history.loadPhoto(entry) }?.let { photo ->
                if (generation == puzzleGeneration && nav == initialNavigation && puzzle == null) {
                    puzzle = restore(entry, photo)
                    nav = Navigation(Screen.PUZZLE, listOf(Screen.CAMERA))
                }
            }
        }
    }

    val drawer = rememberDrawerState(DrawerValue.Closed)

    // A drawer that was shut must stay shut when the phone is turned.
    //
    // It did not. Turning the phone from portrait to landscape opened the drawer on its
    // own, over whatever was on screen, and only in that direction - which is what gives
    // it away. The drawer is anchored in pixels: shut sits at minus its own width, about
    // -907 on this screen. Widen the screen and shut becomes about -2026 while the offset
    // stays where it was, so the nearest anchor is no longer the one it is resting on, and
    // it settles open. Going the other way the offset is beyond shut and clamps to it,
    // which is why turning back was always harmless.
    //
    // So the value is put back after every width change. What the drawer was doing before
    // the change is tracked rather than assumed, because an open drawer should stay open.
    val wide = LocalConfiguration.current.screenWidthDp
    val wanted = remember { mutableStateOf(DrawerValue.Closed) }
    LaunchedEffect(wide) {
        drawer.snapTo(wanted.value)
        snapshotFlow { drawer.currentValue }.collect { wanted.value = it }
    }

    fun closeDrawer() {
        scope.launch { drawer.close() }
    }

    fun openDrawer() {
        if (storageBusy) return
        scope.launch { drawer.open() }
    }

    LaunchedEffect(drawer) {
        snapshotFlow { drawer.isOpen }.collect { open ->
            if (open) {
                entries = storage.withLock { withContext(Dispatchers.IO) { history.list() } }
                refused = withContext(Dispatchers.IO) { Diagnostics.refused(context) }
            }
        }
    }

    suspend fun recordSuccessfulSubmission(
        snapshot: PuzzleState,
        id: Long?,
        receipt: SubmissionReceipt,
    ) {
        SubmissionReceiptStore.add(context, receipt)
        submissionReceipts = SubmissionReceiptStore.list(context)
        id?.let {
            pendingAutoSubmissions.markSubmitted(
                it, snapshot.readingCorrections.size,
            )
        }
        val active = puzzle?.takeIf {
            if (id != null) entryId == id else it.photo === snapshot.photo
        }
        if (active != null) {
            puzzle = active.copy(submittedCorrectionCount = maxOf(
                active.submittedCorrectionCount, snapshot.readingCorrections.size,
            ), submissionReceipts = if (active.submissionReceipts.any { it.digest == receipt.digest }) {
                active.submissionReceipts
            } else active.submissionReceipts + receipt)
        }
        if (id != null) {
            storage.withLock {
                withContext(Dispatchers.IO) {
                    history.markSubmitted(id, snapshot.readingCorrections.size, receipt)
                }
            }
            entries = withContext(Dispatchers.IO) { history.list() }
        }
    }

    suspend fun uploadSnapshot(snapshot: PuzzleState, id: Long?): Boolean {
        val result = MisreadUploader.upload(snapshot)
        val receipt = result.getOrNull()
        if (receipt != null) {
            recordSuccessfulSubmission(snapshot, id, receipt)
            return true
        }
        id?.let {
            pendingAutoSubmissions.discardThrough(it, snapshot.readingCorrections.size)
        }
        Toast.makeText(context,
            "Could not submit the reading. Please try again when connected.",
            Toast.LENGTH_LONG).show()
        return false
    }

    fun isPendingAutoSubmission(state: PuzzleState): Boolean =
        state.originalUncertainCells.isNotEmpty() &&
            (state.submittedCorrectionCount < 0 ||
                state.readingCorrections.size > state.submittedCorrectionCount)

    fun enqueueAutoSubmission(id: Long?, state: PuzzleState) {
        if (id != null && isPendingAutoSubmission(state)) {
            pendingAutoSubmissions.offer(
                id, state.photo, state.readingCorrections.size, state,
            )
        }
    }

    var drainQueuedAutoSubmission: () -> Unit = {}

    fun submitAutomatically(
        id: Long?,
        expectedPhoto: android.graphics.Bitmap? = null,
        queuedSnapshot: PuzzleState? = null,
    ) {
        if (!settings.autoShareWhenUncertain || !MisreadSubmission.available) return
        val initial = queuedSnapshot ?: puzzle ?: return
        if (expectedPhoto != null && initial.photo !== expectedPhoto) return
        if (!isPendingAutoSubmission(initial)) return
        if (submissionInFlight) {
            enqueueAutoSubmission(id, initial)
            return
        }
        submissionInFlight = true
        scope.launch {
            try {
                while (settings.autoShareWhenUncertain) {
                    val active = puzzle?.takeIf { entryId == id && it.photo === initial.photo }
                    val snapshot = active ?: initial
                    if (!isPendingAutoSubmission(snapshot)) break
                    if (!uploadSnapshot(snapshot, id)) break
                    if (active == null || puzzle?.photo !== initial.photo || entryId != id) break
                }
            } finally {
                submissionInFlight = false
                drainQueuedAutoSubmission()
            }
        }
    }

    drainQueuedAutoSubmission = drain@{
        if (!settings.autoShareWhenUncertain) {
            pendingAutoSubmissions.clear()
            return@drain
        }
        while (true) {
            val item = pendingAutoSubmissions.poll() ?: return@drain
            val active = puzzle?.takeIf {
                entryId == item.entryId && it.photo === item.photo
            }
            val snapshot = active ?: item.payload
            if (!isPendingAutoSubmission(snapshot)) continue
            submitAutomatically(item.entryId, snapshot.photo, snapshot)
            return@drain
        }
    }

    // Re-attempt an opted-in report that was left pending by an interrupted/offline send
    // when its history entry is restored in a later app session.
    LaunchedEffect(puzzle?.photo, entryId, settings.autoShareWhenUncertain) {
        if (entryId != null && settings.autoShareWhenUncertain) submitAutomatically(entryId)
    }

    fun submitManually(snapshot: PuzzleState, shareAutomatically: Boolean) {
        settings = settings.copy(autoShareWhenUncertain = shareAutomatically)
        Settings.save(context, settings)
        if (!shareAutomatically) pendingAutoSubmissions.clear()
        if (!MisreadSubmission.available || submissionInFlight) return
        val submissionId = entryId
        submissionInFlight = true
        scope.launch {
            try {
                if (uploadSnapshot(snapshot, submissionId)) {
                    Toast.makeText(context, "Reading submitted. Thank you!", Toast.LENGTH_SHORT).show()
                }
            } finally {
                submissionInFlight = false
                drainQueuedAutoSubmission()
            }
        }
    }

    fun editPuzzle(updated: PuzzleState) {
        val previous = puzzle
        puzzle = updated
        if (settings.autoShareWhenUncertain &&
            isPendingAutoSubmission(updated)) {
            submitAutomatically(entryId)
        }
        if (previous != null && previous.grid == updated.grid &&
                HistoryDetails.of(previous) == HistoryDetails.of(updated)) return
        entryId?.let { id ->
            entries = entries.map { entry ->
                if (entry.id == id) entry.copy(grid = updated.grid, details = HistoryDetails.of(updated))
                else entry
            }
            scope.launch {
                val result = storage.withLock {
                    withContext(Dispatchers.IO) {
                        runCatching { history.update(id, updated) }
                    }
                }
                if (result.isFailure) storageError = "Your changes are visible, but could not be saved. " +
                    "Free some storage and try editing the puzzle again."
            }
        }
    }

    fun applySettings(updated: Settings) {
        if (!updated.autoShareWhenUncertain) pendingAutoSubmissions.clear()
        settings = updated
        Settings.save(context, updated)
        // A puzzle already on screen should follow the setting rather than keep the old one.
        puzzle = puzzle?.copy(
            hintStyle = updated.hintStyle,
            routeStyle = updated.routeStyle,
        )
    }

    fun go(target: Screen) {
        nav = nav.go(target)
    }

    /** The close button on a screen you opened. The same thing Back does there. */
    fun leaveOverlay() {
        nav = nav.back() ?: nav.reset(if (puzzle != null) Screen.PUZZLE else Screen.CAMERA)
    }

    /**
     * Going to the camera without throwing the puzzle away.
     *
     * Keeping it is what makes Back from the camera mean "never mind" rather than "leave
     * the app". A photograph that is actually taken replaces it.
     */
    fun takePhoto() {
        puzzleGeneration++
        go(Screen.CAMERA)
    }

    /** The puzzle on screen is gone - deleted - so there is nothing to go back to. */
    fun discardPuzzle() {
        puzzleGeneration++
        puzzle = null
        entryId = null
        nav = nav.reset(Screen.CAMERA)
    }

    // Back has to mean something everywhere it can. Every one of these was, at some
    // point, a press that closed the app instead: the drawer has no scrim to tap on a
    // narrow phone, and settings, about, the tutor and the puzzle itself all sat one
    // press from the door.
    //
    // What Back undoes is the last thing that appeared: the drawer, then a screen you
    // opened, then a layer over the puzzle, then the puzzle itself. Only on the camera
    // with nothing behind it does Back leave, which is the one place Android expects to
    // be let out of. The conditions are exclusive, but they are still written
    // outermost-first: the dispatcher runs the last enabled handler declared, so the
    // drawer - the topmost thing on screen whenever it is open - goes at the bottom.
    BackHandler(enabled = !drawer.isOpen && nav.canGoBack) {
        nav.back()?.let { nav = it }
    }
    BackHandler(
        enabled = !drawer.isOpen &&
            screen == Screen.PUZZLE &&
            puzzle?.overlay?.let { it != OverlayMode.NONE } == true,
    ) {
        puzzle = puzzle?.close()
    }
    BackHandler(enabled = drawer.isOpen) { closeDrawer() }
    BackHandler(enabled = storageBusy) { /* Finish the current save/load before navigating. */ }

    ModalNavigationDrawer(
        drawerState = drawer,
        // A settings or about screen is somewhere you went on purpose; sliding history in
        // over it would be answering a question nobody asked.
        gesturesEnabled = !storageBusy &&
            (drawer.isOpen || screen == Screen.CAMERA || screen == Screen.PUZZLE),
        // A reading page is somewhere you went on purpose; sliding history in over it
        // would be answering a question nobody asked.
        drawerContent = {
            HistoryDrawer(
                entries = entries,
                currentId = entryId,
                refused = refused,
                onOpen = { entry ->
                    if (!storageBusy) {
                        val generation = ++puzzleGeneration
                        storageBusy = true
                        scope.launch {
                            val saved = storage.withLock {
                                withContext(Dispatchers.IO) {
                                    history.list().firstOrNull { it.id == entry.id }?.let { current ->
                                        history.loadPhoto(current)?.let { current to it }
                                    }
                                }
                            }
                            if (saved != null && generation == puzzleGeneration) {
                                entryId = saved.first.id
                                puzzle = restore(saved.first, saved.second)
                                go(Screen.PUZZLE)
                            } else if (generation == puzzleGeneration) {
                                storageError = "This puzzle's photo could not be opened."
                            }
                            storageBusy = false
                        }
                        closeDrawer()
                    }
                },
                onDelete = { entry ->
                    scope.launch {
                        entries = storage.withLock {
                            withContext(Dispatchers.IO) { history.delete(entry); history.list() }
                        }
                    }
                    // The puzzle on screen has just been thrown away, so leave it.
                    if (entryId == entry.id) discardPuzzle()
                },
                onCamera = {
                    takePhoto()
                    closeDrawer()
                },
                onDiscard = { scan ->
                    scope.launch {
                        refused = withContext(Dispatchers.IO) {
                            Diagnostics.discard(scan)
                            Diagnostics.refused(context)
                        }
                    }
                },
                onClose = ::closeDrawer,
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
        when {
            screen == Screen.STRATEGIES -> StrategiesScreen(
                // PuzzleState already caches this; calling the solver here instead ran
                // all twenty-three techniques again on every recomposition, on the main
                // thread, for a number it was holding the whole time.
                findings = puzzle?.findingCounts.orEmpty(),
                onExplore = { technique ->
                    puzzle = puzzle?.tutor(technique)
                    leaveOverlay()
                },
                onClose = ::leaveOverlay,
            )

            screen == Screen.ABOUT -> AboutScreen(onClose = ::leaveOverlay)

            screen == Screen.SETTINGS -> SettingsScreen(
                settings = settings,
                submissionReceipts = submissionReceipts,
                onChange = ::applySettings,
                onClose = ::leaveOverlay,
            )

            screen == Screen.PUZZLE && puzzle != null -> PreparedPuzzleScreen(
                state = puzzle!!,
                onChange = ::editPuzzle,
                onMenu = ::openDrawer,
                onRetake = ::takePhoto,
                onStrategies = { go(Screen.STRATEGIES) },
                onSettings = { go(Screen.SETTINGS) },
                onAbout = { go(Screen.ABOUT) },
                autoShareUncertain = settings.autoShareWhenUncertain,
                submissionInFlight = submissionInFlight,
                onSubmitReading = ::submitManually,
            )

            !hasCamera -> PermissionScreen(
                onRequest = { request.launch(Manifest.permission.CAMERA) },
                onSettings = {
                    context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")))
                },
                onMenu = ::openDrawer,
            )

            else -> CameraScreen(
                autoCapture = settings.autoCapture && drawer.isClosed && !drawer.isAnimationRunning,
                onRead = { state ->
                    val generation = ++puzzleGeneration
                    // Saved as soon as it is read, so a puzzle is never lost by backing out.
                    entryId = null
                    puzzle = state.copy(
                        hintStyle = settings.hintStyle,
                        routeStyle = settings.routeStyle,
                    )
                    go(Screen.PUZZLE)
                    storageBusy = true
                    val captured = puzzle!!
                    scope.launch {
                        val result = storage.withLock {
                            withContext(Dispatchers.IO) { runCatching { history.save(captured) to history.list() } }
                        }
                        result.onSuccess { (entry, saved) ->
                            if (generation == puzzleGeneration) entryId = entry.id
                            entries = saved
                            if (generation == puzzleGeneration && captured.originalUncertainCells.isNotEmpty()) {
                                submitAutomatically(entry.id)
                            }
                        }
                            .onFailure { storageError = "This puzzle could not be saved. " +
                                "You can still use it now. Free some storage before taking another photo." }
                        storageBusy = false
                    }
                },
                onMenu = ::openDrawer,
                onStrategies = { go(Screen.STRATEGIES) },
                onSettings = { go(Screen.SETTINGS) },
                onAbout = { go(Screen.ABOUT) },
            )
        }
        if (storageBusy) Surface(Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        }
    }
    storageError?.let { message ->
        AlertDialog(onDismissRequest = { storageError = null }, title = { Text("Puzzle storage") },
            text = { Text(message) }, confirmButton = {
                TextButton(onClick = { storageError = null }) { Text("OK") }
            })
    }
}

/**
 * The puzzles behind the drawer.
 *
 * Narrower than the screen on purpose, so there is always a strip of scrim to tap. The
 * default is 360dp, which is wider than a small phone - and the drawer has no other way
 * out on one, which is how Back came to be the only way to close it.
 */
@Composable
private fun HistoryDrawer(
    entries: List<HistoryEntry>,
    currentId: Long?,
    refused: List<Diagnostics.Refused>,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onCamera: () -> Unit,
    onDiscard: (Diagnostics.Refused) -> Unit,
    onClose: () -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.84f)) {
        HistoryList(
            entries = entries,
            currentId = currentId,
            onOpen = onOpen,
            onDelete = onDelete,
            onCamera = onCamera,
            refused = refused,
            onDiscard = onDiscard,
            onClose = onClose,
        )
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onSettings: () -> Unit, onMenu: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                "Sudoku Buddy reads puzzles through the camera, so it needs camera access. " +
                    "Nothing leaves your phone - the app has no internet permission at all.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onRequest) { Text("Allow camera") }
            TextButton(onClick = onSettings) { Text("Open app settings") }
            TextButton(onClick = onMenu) { Text("Your saved puzzles") }
        }
    }
}
