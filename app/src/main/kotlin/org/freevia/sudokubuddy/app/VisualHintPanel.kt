package org.freevia.sudokubuddy.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import org.freevia.sudokubuddy.solver.Deduction

/** Lives in the existing scrolling controls pane, leaving the board fixed in place. */
@Composable
internal fun VisualHintPanel(state: PuzzleState, onChange: (PuzzleState) -> Unit) {
    val hint = state.visualHint ?: return
    val navigation = state.hintNavigation
    val frame = navigation.frame
    val lesson = frame.step?.let { hint.proof.getOrNull(it) }
    var showEarlier by remember(hint, frame.step) { mutableStateOf(false) }
    fun changeFrame(next: HintFrame) = onChange(state.copy(hintNavigation = navigation.copy(frame = next)))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            VisualHint.stageNames.forEachIndexed { depth, label ->
                TextButton(onClick = { onChange(state.copy(hintDepth = depth,
                    hintNavigation = HintNavigation())) }, enabled = depth != state.hintDepth) {
                    Text(localized("${depth + 1}. $label"))
                }
            }
        }
        Text(localized(VisualHint.KEY_CAPTION),
            style = MaterialTheme.typography.bodySmall)
        when (state.hintDepth) {
            0 -> Text(localized(VisualHint.stageCaption(hint, state.hintDepth)))
            1 -> Text(localized(VisualHint.stageCaption(hint, state.hintDepth)))
            else -> {
                if (lesson == null) {
                    if (state.hintDepth == 3) {
                        Text(localized(VisualHint.stageCaption(hint, state.hintDepth)), style = MaterialTheme.typography.titleMedium)
                        Text(localized(VisualHint.ANSWER_GUIDANCE))
                    } else Text(localized(VisualHint.stageCaption(hint, state.hintDepth)))
                    val branches = VisualHint.branches(hint)
                    for (step in branches) {
                        TextButton(onClick = { onChange(state.copy(hintNavigation = navigation.visit(step))) }) {
                            Text(localized(VisualHint.label(hint, step)))
                        }
                    }
                    // Hidden singles and other placement proofs may have evidence outside
                    // the target without directly eliminating a target candidate.
                    TextButton(onClick = { onChange(state.copy(hintNavigation = navigation.visit(hint.proof.lastIndex))) }) {
                        Text(localized("Why this square? · ${hint.technique}"))
                    }
                } else {
                    val firstQuestion = navigation.parents.firstNotNullOfOrNull { it.step } ?: frame.step!!
                    Text(localized("Your square ← ${VisualHint.label(hint, firstQuestion)}" +
                        if (navigation.parents.size > 1) " ← earlier evidence (depth ${navigation.parents.size - 1})" else ""),
                        style = MaterialTheme.typography.labelLarge)
                    Text(localized(lesson.deduction.technique), style = MaterialTheme.typography.titleMedium)
                    if (lesson.dependencies.isNotEmpty()) {
                        Text(localized("This view includes earlier candidate removals. Open their evidence below to check them."),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        TextButton(modifier = Modifier.weight(1f), onClick = { changeFrame(frame.copy(before = true)) }, enabled = !frame.before) { Text(localized("Before")) }
                        TextButton(modifier = Modifier.weight(1f), onClick = { changeFrame(frame.copy(before = false)) }, enabled = frame.before) {
                            Text(localized(if ((lesson.deduction as? Deduction.Elimination)?.chain != null) "Test assumption" else "After"))
                        }
                    }
                    Text(localized(VisualHint.caption(hint, frame)))
                    val chain = (lesson.deduction as? Deduction.Elimination)?.chain
                    if (chain != null && !frame.before) {
                        Text(localized(when {
                            frame.consequence > chain.links.size -> "Assumption rejected · real candidates restored"
                            frame.consequence == chain.links.size -> "Contradiction · assumption fails"
                            frame.consequence == 0 -> "Assumption · hypothetical digit"
                            else -> "Consequence ${frame.consequence} of ${chain.links.size - 1} · hypothetical digits"
                        }),
                            style = MaterialTheme.typography.labelSmall)
                        Row {
                            TextButton(modifier = Modifier.weight(1f), onClick = { changeFrame(frame.copy(consequence = frame.consequence - 1)) },
                                enabled = frame.consequence > 0) { Text(localized("Previous")) }
                            TextButton(modifier = Modifier.weight(1f), onClick = { changeFrame(frame.copy(consequence = frame.consequence + 1)) },
                                enabled = frame.consequence <= chain.links.size) {
                                Text(localized(when (frame.consequence) {
                                    chain.links.size - 1 -> "Show contradiction"
                                    chain.links.size -> "Reject assumption"
                                    else -> "Next consequence"
                                }))
                            }
                        }
                    }
                    if (lesson.dependencies.isNotEmpty()) {
                        TextButton(onClick = { showEarlier = !showEarlier }) {
                            Text(localized(if (showEarlier) "Hide earlier evidence" else
                                "Explain earlier candidate removals (${lesson.dependencies.size})"))
                        }
                    }
                    if (lesson.dependencies.isNotEmpty() && showEarlier) {
                        val target = when (val move = lesson.deduction) {
                            is Deduction.Placement -> move.index
                            is Deduction.Elimination -> move.fromCells.singleOrNull() ?: -1
                        }
                        for (step in lesson.dependencies.sorted()) {
                            TextButton(onClick = { onChange(state.copy(hintNavigation = navigation.visit(step))) }) {
                                Text(localized(VisualHint.label(hint, step, target)))
                            }
                        }
                    }
                    TextButton(onClick = { onChange(state.copy(hintNavigation = navigation.back())) }) {
                        Text(localized(if (navigation.parents.size <= 1) "Back to your square" else "Back to the previous question"))
                    }
                }
                TextButton(onClick = { onChange(state.showHintProof()) }) { Text(localized("Follow the full proof in order")) }
            }
        }
        TextButton(onClick = { onChange(state.close()) }) { Text(localized("Close hint")) }
    }
}
