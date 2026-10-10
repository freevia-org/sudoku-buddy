package org.freevia.sudokubuddy.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.freevia.sudokubuddy.solver.Walkthrough
import org.freevia.sudokubuddy.solver.Deduction
import org.freevia.sudokubuddy.solver.hasTeachingProof
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
/*
 * The tutor: a panel that pulls up from the bottom over the layer buttons.
 *
 * Split out of PuzzleScreen. It is the largest single thing the screen does and the only
 * part with a gesture and an animation of its own.
 */
/** The grab bar. The same one whether the panel is resting or open. */
@Composable
private fun Handle() {
    Box(
        Modifier
            .size(width = 36.dp, height = 4.dp)
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(2.dp),
            )
    )
}

/**
 * What a step of the route says, wherever it is being said.
 *
 * Shared because the sheet and the pane want the same words in the same order: what is
 * true of this position, then the technique's how-to folded away, then what the move
 * actually does.
 */
@Composable
internal fun ColumnScope.Lesson(state: PuzzleState, trailing: @Composable () -> Unit = {}) {
    val guidance = state.guidance ?: return

    if (state.overlay == OverlayMode.HINT || state.overlay == OverlayMode.LESSON) {
        state.reasoningNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (guidance.effect == null) {
        Trailing(guidance.body, MaterialTheme.typography.bodyMedium, null, trailing)
    } else {
        Text(guidance.body, style = MaterialTheme.typography.bodyMedium)
        // Last, because it is the summary of a move whose reasoning is above it.
        Trailing(
            guidance.effect,
            MaterialTheme.typography.bodySmall,
            MaterialTheme.colorScheme.onSurfaceVariant,
            trailing,
        )
    }
}

/**
 * A paragraph with a small control after its last word rather than under it.
 *
 * The text takes only the width it needs, so a short paragraph leaves the control beside
 * it and a long one leaves it beside the last line. Aligned to the bottom, which is what
 * puts it on that last line rather than the first.
 */
@Composable
private fun Trailing(
    text: String,
    style: TextStyle,
    colour: Color?,
    trailing: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text,
            style = style,
            color = colour ?: Color.Unspecified,
            modifier = Modifier.weight(1f, fill = false),
        )
        trailing()
    }
}

/**
 * The tutor: one panel, resting across the foot of the screen or filling it.
 *
 * It was two things before - a labelled band at the bottom, and a separate sheet that
 * appeared once the band had been dragged far enough. Dragging the band therefore moved
 * the band, and the panel arrived afterwards, which is not the same gesture at all. This
 * is one Surface whose height changes: what you drag is the thing that grows, and letting
 * go settles it to whichever end it is nearer.
 *
 * Resting, only its title shows. Everything below the title is laid out as usual and
 * simply clipped away, so the open panel is the same panel and not a second one.
 */
@Composable
internal fun BoxScope.TutorPanel(
    state: PuzzleState,
    route: Walkthrough,
    onChange: (PuzzleState) -> Unit,
    peek: Dp,
    full: Dp,
) {
    val density = LocalDensity.current
    val fullPx = with(density) { full.toPx() }.coerceAtLeast(0f)
    // A short split-screen window can leave less space than the normal handle.
    // Keep the drag range valid while the window is resized through that state.
    val peekPx = with(density) { peek.toPx() }.coerceIn(0f, fullPx)
    val open = state.overlay == OverlayMode.LESSON
    val scope = rememberCoroutineScope()

    // One value for the height, which the finger writes to directly and an animation
    // settles afterwards. It used to be an animateFloatAsState racing a separate drag
    // offset: opening began the animation, the animation finished while the finger was
    // still down, and letting go handed control back to a value that had long since
    // reached the top - so the panel jumped, and the whole opening played again.
    val height = remember { Animatable(peekPx) }
    var dragging by remember { mutableStateOf(false) }

    // The only thing that settles the panel, so it can only ever come to rest open or
    // shut. Letting the drag handler animate as well left it stopped halfway whenever the
    // two disagreed about which of them was finishing the job.
    LaunchedEffect(open, dragging, peekPx, fullPx) {
        if (!dragging) height.animateTo(if (open) fullPx else peekPx)
    }

    val heightPx = height.value
    val last = PuzzleLogic.lastStep(route)
    val at = state.lessonStep.coerceIn(0, last)
    val stepAt = with(density) { 48.dp.toPx() }

    // Whether the how-to is showing. Closes itself when the technique changes, which is
    // the moment it would have become the wrong text.
    var asking by remember(state.evidenceLabel, at) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(at, state.tutorTechnique, state.tutorHintProof, state.lessonBefore, state.practicing) {
        scroll.scrollTo(0)
    }

    fun stepBy(by: Int) {
        val to = at + by
        if (to in 0..last) onChange(state.stepTo(to))
    }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(with(density) { heightPx.toDp() })
            .clipToBounds(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            // Tight at the top, because everything up there is a label or a control and
            // the room it takes comes straight out of the explanation underneath.
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // The grip: the handle and the title under it. Drag either way, or tap to
            // open and tap again to shut. It is the only part of the panel that is always
            // on screen, so it is the only part that can be the way in or out.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        // Opened the moment the drag begins, not when it is let go, so that
                        // what is dragged into view is what stays there. Waiting until the
                        // release meant the panel showed nothing while being pulled and its
                        // first page once let go, which changed under the reader exactly as
                        // the movement ended.
                        onDragStarted = {
                            dragging = true
                            if (!open) onChange(state.reopenTutor())
                        },
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                height.snapTo((height.value - delta).coerceIn(peekPx, fullPx))
                            }
                        },
                        onDragStopped = { velocity ->
                            val wanted = when {
                                velocity < -600f -> true
                                velocity > 600f -> false
                                else -> height.value > (peekPx + fullPx) / 2f
                            }
                            if (wanted != open) {
                                onChange(if (wanted) state.reopenTutor() else state.close())
                            }
                            dragging = false
                        },
                    )
                    .clickable { onChange(if (open) state.close() else state.reopenTutor()) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(modifier = Modifier.padding(top = 6.dp)) { Handle() }

                // The name on the left where a title belongs, and the stepping centred,
                // because it is the control your thumb goes to and the middle is where a
                // thumb lands. A Box rather than a Row so the middle is the middle of the
                // panel and not of whatever is left over beside the title.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (state.tutorHintProof) "Hint proof" else "Tutor",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )

                    if (open) {
                        Stepping(
                            at = at,
                            last = last,
                            steps = route.steps.size,
                            onStep = ::stepBy,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }

            // Nothing below the grip is worth building while the panel is shut: the route,
            // its chapters and the step's own text all cost a solve, and none of it can be
            // seen.
            if (heightPx > peekPx + 1f) {
                OpenPanel(
                    state = state,
                    onChange = onChange,
                    at = at,
                    scroll = scroll,
                    asking = asking,
                    onAsk = { asking = !asking },
                    stepAt = stepAt,
                    onStep = ::stepBy,
                )
            }
        }
    }
}

/** Back and forward by exactly one step, for when swiping is too coarse. */
@Composable
private fun Stepping(at: Int, last: Int, steps: Int, onStep: (Int) -> Unit, modifier: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        IconButton(onClick = { onStep(-1) }, enabled = at > 0) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous step",
            )
        }
        Text("$at / $steps", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        IconButton(onClick = { onStep(1) }, enabled = at < last) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next step",
            )
        }
    }
}

/**
 * A thread of a scrollbar, so it is visible that there is more below without anything
 * being spent on saying so.
 */
internal fun Modifier.scrollThread(scroll: ScrollState, colour: Color) = drawWithContent {
    drawContent()
    if (scroll.maxValue <= 0) return@drawWithContent
    val track = size.height
    if (track <= 0f) return@drawWithContent
    val thumb = (track * track / (track + scroll.maxValue)).coerceIn(minOf(24.dp.toPx(), track), track)
    val width = 3.dp.toPx()
    drawRoundRect(
        color = colour,
        topLeft = Offset(
            size.width - width,
            (track - thumb) * (scroll.value.toFloat() / scroll.maxValue),
        ),
        size = Size(width, thumb),
        cornerRadius = CornerRadius(width / 2f),
    )
}

/**
 * The panel once it is open: what is being walked, how far through it you are, and the
 * step itself.
 *
 * Built only when there is room to see it. The route, its chapters and the step's own text
 * all cost a solve, and none of it can be seen while the panel rests.
 */
@Composable
private fun ColumnScope.OpenPanel(
    state: PuzzleState,
    onChange: (PuzzleState) -> Unit,
    at: Int,
    scroll: ScrollState,
    asking: Boolean,
    onAsk: () -> Unit,
    stepAt: Float,
    onStep: (Int) -> Unit,
) {
    val practiceScope = rememberCoroutineScope()
    val latestState = rememberUpdatedState(state)
    val latestChange = rememberUpdatedState(onChange)
    var checking by remember(state) { mutableStateOf(false) }
    // One line for what is being walked, what the colours mean, and how far through this
    // run of the technique you are. The technique's name was being printed twice - once
    // here and once in the key beside the colour of its own squares - and the key is the
    // one that earns it.
    Row(verticalAlignment = Alignment.CenterVertically) {
        TutorPicker(state, onChange, state.routeLength, Modifier)
        Legend(state.legend, modifier = Modifier.weight(1f), evidenceLabel = state.evidenceLabel)
        ChapterCount(state.chapters, at - 1)
    }

    // Minus one, because the strip is a picture of the route and the route starts at step
    // one; step zero is the tutor talking about it.
    ChapterStrip(state.chapters, at - 1) { onChange(state.stepTo(it + 1)) }

    // Sideways for the next step, so the common move needs no button at all.
    val step = rememberUpdatedState(onStep)
    val threshold = rememberUpdatedState(stepAt)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .scrollThread(scroll, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            .verticalScroll(scroll)
            // Installed once, and reading the current stepper when a swipe finishes
            // rather than the one it was built with. Keyed on the step, it held the
            // stepper made for that step - and the stepper carries the whole state with
            // it, so correcting a square while the tutor was open and then swiping put
            // the correction back the way it was.
            .pointerInput(Unit) {
                var swiped = 0f
                detectHorizontalDragGestures(
                    onDragStart = { swiped = 0f },
                    onDragEnd = {
                        when {
                            swiped < -threshold.value -> step.value(1)
                            swiped > threshold.value -> step.value(-1)
                        }
                    },
                ) { _, amount -> swiped += amount }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(2.dp))
        if (state.tutorHintProof) {
            TextButton(onClick = { onChange(state.copy(overlay = OverlayMode.HINT,
                hintStyle = HintStyle.EXPLAIN, tutorHintProof = false, practice = false,
                practiceCell = null, practiceFeedback = null)) }) { Text("Back to visual hint") }
        }
        if (at == 0) {
            Text(if (state.tutorTechnique == null) {
                "Follow a route where each move builds on the previous one."
            } else "Examples available now: each starts from your current board.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (at > 0 && state.currentDeduction?.hasTeachingProof == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Try it yourself", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge)
                Switch(checked = state.practice, onCheckedChange = {
                    onChange(state.copy(practice = it, practiceRevealed = false,
                        practiceCell = null, practiceFeedback = null, lessonBefore = false))
                })
            }
        }
        if (state.practicing) {
            state.reasoningNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            val move = state.currentDeduction
            Text(if (move is Deduction.Elimination) {
                "Using ${move.technique}, tap one square where a candidate can be removed, then choose that digit."
            } else {
                "Using ${move?.technique}, tap the square you can fill, then choose its digit."
            })
            state.practiceCell?.let {
                Text("Selected: row ${it / 9 + 1}, column ${it % 9 + 1}",
                    style = MaterialTheme.typography.labelLarge)
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                for (digit in 1..9) TextButton(enabled = !checking, onClick = {
                    checking = true
                    practiceScope.launch {
                        val answer = withContext(Dispatchers.Default) { state.practiceAnswer(digit) }
                        if (latestState.value === state) {
                            checking = false
                            latestChange.value(answer)
                        }
                    }
                }) {
                    Text("$digit")
                }
            }
            state.practiceFeedback?.let { Text(it) }
            Row {
                TextButton(onClick = {
                    onChange(state.copy(practiceRevealed = true, lessonBefore = false))
                }) { Text("Reveal explanation") }
                state.guidance?.howTo?.let { HowTo(asking, onAsk) }
            }
        } else {
            if (at > 0 && state.walkthrough?.lessons?.getOrNull(at - 1) != null) {
                Row {
                    TextButton(onClick = { onChange(state.copy(lessonBefore = true)) },
                        enabled = !state.lessonBefore) { Text("Before") }
                    TextButton(onClick = { onChange(state.copy(lessonBefore = false)) },
                        enabled = state.lessonBefore) { Text("After") }
                    Text(if (state.lessonBefore) "Before this move" else "After this move",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.CenterVertically))
                }
                if (state.lessonBefore) {
                    Text("Switch to After to see the move and its consequences on the grid.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            state.practiceFeedback?.let { Text(it) }
            Lesson(state) {
                state.guidance?.howTo?.let { HowTo(asking, onAsk) }
            }
            state.walkthrough?.lessons?.getOrNull(at - 1)?.dependencies?.takeIf { it.isNotEmpty() }?.let {
                Text("Earlier steps used in this proof", style = MaterialTheme.typography.labelLarge)
                for (dependency in it.sorted()) {
                    val earlier = state.walkthrough?.steps?.getOrNull(dependency)
                    val reason = if (earlier is Deduction.Elimination) {
                        val target = (state.currentDeduction as? Deduction.Placement)?.index
                        if (target != null && target in earlier.fromCells) {
                            "Why ${earlier.digit} was removed from r${target / 9 + 1}c${target % 9 + 1}"
                        } else "${earlier.technique}: remove ${earlier.digit}"
                    } else earlier?.technique.orEmpty()
                    TextButton(onClick = { onChange(state.visitReason(dependency + 1)) }) {
                        Text("Step ${dependency + 1}: $reason")
                    }
                }
            }
            state.returnToStep?.let { step ->
                TextButton(onClick = { onChange(state.stepTo(step)) }) { Text("Return to step $step") }
            }
            if (state.tutorHintProof && at == state.walkthrough?.steps?.size) {
                TextButton(onClick = { onChange(state.tutor()) }) { Text("Continue with the full route") }
            }
            if (!state.tutorHintProof && state.walkthrough?.cumulative == true &&
                state.walkthrough?.finishes == true && at == state.walkthrough?.steps?.size) {
                Text("Route complete. Go back to review any move, or try the steps yourself.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        if (asking) {
            state.guidance?.howTo?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The technique's how-to, under the step it belongs to.
 *
 * Paragraphs long and the same words every time that technique comes round, so it is not
 * printed until it is asked for. It sits at the end of the step rather than in the
 * panel's header, where a question mark beside the stepping controls looked like help
 * with the controls.
 */
@Composable
private fun HowTo(open: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 90f else 0f, label = "how")

    Row(
        modifier = Modifier.clickable(onClick = onToggle).padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "how",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (open) "Hide how to spot one" else "How to spot one",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
        )
    }
}

/** How much of the tutor shows when it is shut: its handle and its title. */
internal val TUTOR_PEEK = 56.dp

/**
 * Scrollable chapter names and step ranges, with a full button-sized touch target.
 */
@Composable
private fun ChapterStrip(chapters: List<Chapter>, at: Int, onJump: (Int) -> Unit) {
    if (chapters.isEmpty()) return
    val here = chapters.firstOrNull { at in it.from until it.until }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        for (chapter in chapters) {
            val colour = when {
                chapter === here -> MaterialTheme.colorScheme.primary
                chapter.until <= at -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            TextButton(onClick = { onJump(chapter.from) }) {
                Text("${chapter.technique} (${chapter.from + 1}–${chapter.until})",
                    color = colour, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * How far through this run of the technique you are.
 *
 * The technique itself is not named here. It is named in the key, beside the colour of the
 * squares it is talking about, which is the one place it earns its width.
 */
@Composable
private fun ChapterCount(chapters: List<Chapter>, at: Int) {
    val here = chapters.firstOrNull { at in it.from until it.until } ?: return
    if (here.count == 1) return
    Text(
        "${at - here.from + 1} of ${here.count}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/**
 * One of the four layers. The selected one is filled, so which is on can be seen without
 * reading; pressing it again turns it off.
 */
