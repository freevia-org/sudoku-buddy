package org.freevia.sudokubuddy.app

import android.graphics.Bitmap
import org.freevia.sudokubuddy.model.Cell
import org.freevia.sudokubuddy.model.CellSource
import org.freevia.sudokubuddy.model.Grid
import org.freevia.sudokubuddy.solver.Chain
import org.freevia.sudokubuddy.solver.Deduction
import org.freevia.sudokubuddy.solver.Hint
import org.freevia.sudokubuddy.solver.RouteStyle
import org.freevia.sudokubuddy.solver.SolveResult
import org.freevia.sudokubuddy.solver.Solver
import org.freevia.sudokubuddy.solver.TechniqueSolver
import org.freevia.sudokubuddy.solver.Techniques
import org.freevia.sudokubuddy.solver.Walkthrough
import org.freevia.sudokubuddy.solver.hasTeachingProof

/**
 * Where the grid lines really are, as fractions of the photograph's width and height.
 *
 * Ten of each. The overlay cannot simply divide by nine: paper is not flat, and the
 * extractor already fits the real lines because a single homography leaves cells
 * progressively misaligned towards the edges. Drawing on ninths would put the tints and
 * digits a few pixels off exactly where the page is most bowed.
 */
data class GridLines(val vertical: List<Float>, val horizontal: List<Float>) {
    init {
        require(vertical.size == 10 && horizontal.size == 10) { "expected ten lines each way" }
    }

    companion object {
        /** Even ninths, for a puzzle reopened from history with no geometry kept. */
        val EVEN = GridLines(List(10) { it / 9f }, List(10) { it / 9f })
    }
}

/**
 * Everything the puzzle screen shows.
 *
 * The rules live in [PuzzleLogic], which knows nothing about Android and is therefore
 * testable without a device. This holds the photograph and what the user has touched.
 */
data class PuzzleState(
    val photo: Bitmap,
    val grid: Grid,
    /** Cells the reader was not sure of. Drawn as a ring, and cleared as they are settled. */
    val uncertainCells: Set<Int>,
    /**
     * What was wrong with the photograph itself, which staying true is the point of.
     *
     * "Move closer - the grid is too small to read" is about the picture and remains
     * true however many squares get corrected, so it is kept apart from [readerComplaint],
     * which is about the puzzle and stops being true the moment the puzzle works.
     */
    val framingNote: String? = null,
    /**
     * What the reader could not make of the digits, while that is still the case.
     *
     * Read through [liveNote] rather than directly. The reader says things like "the
     * printed digits do not make a solvable puzzle", which was written once and then left
     * on screen while the user fixed exactly that - so the app went on complaining about
     * a puzzle that had started solving several corrections ago.
     */
    val readerComplaint: String? = null,
    /** Where the grid lines are, so the overlay lands on the squares it means. */
    val lines: GridLines = GridLines.EVEN,
    /**
     * What the reader made of each square, when this puzzle came from a photograph.
     *
     * The whole list is null for a puzzle reopened from history, where only the grid was
     * kept. A single entry is null once the user has corrected that square: the reading
     * is then no longer what is there, and saying otherwise is worse than saying nothing.
     */
    val reports: List<CellReport?>? = null,
    /** Immutable recognition result, retained when the user corrects a cell. */
    val originalGrid: Grid = grid,
    val originalReports: List<CellReport?>? = reports,
    val originalUncertainCells: Set<Int> = uncertainCells,
    /** Every user correction to a cell that came from the photographed reading. */
    val readingCorrections: List<ReadingCorrection> = emptyList(),
    /** Number of corrections included in the last successful submission; -1 means never sent. */
    val submittedCorrectionCount: Int = -1,
    val submissionReceipts: List<SubmissionReceipt> = emptyList(),
    val overlay: OverlayMode = OverlayMode.NONE,
    val hintStyle: HintStyle = HintStyle.EXPLAIN,
    val selectedCell: Int? = null,
    /** How far the current hint has been pushed. See [PuzzleLogic.HINT_DEPTHS]. */
    val hintDepth: Int = 0,
    /** Which step of the walkthrough is being shown. */
    val lessonStep: Int = 0,
    /**
     * The technique being browsed, when the user has gone looking at one on purpose.
     *
     * Null means the tutor is walking its own route. Set means the user is exploring one
     * technique's findings in this position instead, which is the same machinery pointed
     * at a different list.
     */
    val tutorTechnique: String? = null,
    /** What the route should be good at. See [RouteStyle]. */
    val routeStyle: RouteStyle = RouteStyle.SHORT_CHAINS,
    /**
     * Squares the user typed in themselves.
     *
     * Not derivable from the grid: a digit read from the paper and a digit thumbed in are
     * the same cell, and only one of them is actually written on the photograph. Kept so
     * the second kind can be drawn, since otherwise answering a square changes nothing on
     * screen at all.
     */
    val entered: Set<Int> = emptySet(),
    /**
     * Which answer is being shown, when the puzzle has more than one.
     *
     * Pressing Solve again steps to the next rather than putting the layer away, which is
     * the whole of the browsing: there is nowhere on this screen to put a pair of arrows
     * that would be worth the room, and the button the user just pressed is already under
     * their thumb.
     */
    val answerShown: Int = 0,
    val tutorHintProof: Boolean = false,
    val lessonBefore: Boolean = false,
    val practice: Boolean = false,
    val practiceRevealed: Boolean = false,
    val practiceCell: Int? = null,
    val practiceFeedback: String? = null,
    val returnToStep: Int? = null,
) {
    // Copies made for taps and tutor navigation share grid-dependent work. The cache
    // never retains photos and is bounded so editing/reopening puzzles cannot grow it.
    private val analysis: PuzzleAnalysis by lazy { PuzzleAnalyses.of(grid) }
    val hint: Hint? by lazy { analysis.hint(hintStyle) }

    /** News about the puzzle, when there is any. Null is the ordinary case. */
    val status: Status? by lazy { analysis.status }
    val reasoningNote: String? by lazy { analysis.reasoningNote }

    /**
     * The reading's own complaint, as it stands now rather than as it was first made.
     *
     * The framing half always survives - it is about the photograph. The reader's half
     * only survives while the puzzle still fails to solve, which is what it was about.
     */
    val liveNote: String? by lazy { analysis.readingNote(framingNote, readerComplaint) }

    /**
     * The flagged squares that are still worth asking about. See [PuzzleLogic.stillInQuestion].
     *
     * Everything on screen asks this rather than [uncertainCells]: the banner, its count,
     * and the bars drawn on the photograph. [uncertainCells] stays as the reader left it,
     * less whatever the user has settled, so that a correction which makes the puzzle
     * solvable and a later one which breaks it again do not lose the original doubts.
     */
    val openQuestions: Set<Int> by lazy { analysis.openQuestions(uncertainCells, reports) }
    val readingHeadline: String by lazy { analysis.readingHeadline(openQuestions.size) }

    /** How much is left, for the counter under the grid. */
    val progress: String by lazy { PuzzleLogic.progress(grid) }

    val guidance: Guidance? by lazy {
        analysis.guidance(displayKey) {
            if (tutorHintProof && overlay == OverlayMode.LESSON && lessonStep == 0) {
                return@guidance Guidance("This proof explains one hint, including the candidate " +
                    "eliminations leading to its answer. Step forward to follow the reasoning " +
                    "on the grid, then continue with the full route.")
            }
            PuzzleLogic.guidance(
                grid, overlay, hintStyle, hintDepth, walkthrough, lessonStep, tutorTechnique,
                answerShown,
            )
        }
    }

    val legend: List<LegendKey>
        get() = PuzzleLogic.legend(computed, overlay, openQuestions.isNotEmpty())

    /** What to call the evidence colour in the key: the technique it belongs to. */
    val evidenceLabel: String? by lazy {
        analysis.evidenceLabel(displayKey) {
            PuzzleLogic.evidenceLabel(
                grid, overlay, hintStyle, hintDepth, walkthrough, lessonStep,
            )
        }
    }

    /**
     * The whole route from here to the answer, in human steps.
     *
     * Costs a full technique solve, so it is worked out once per state and only when
     * something asks for it - which the button offering it does, on every recomposition.
     */
    val walkthrough: Walkthrough? by lazy {
        if (tutorHintProof) {
            val explained = (if (hintStyle == HintStyle.EXPLAIN) hint
                else analysis.hint(HintStyle.EXPLAIN)) as? Hint.Explained
            return@lazy explained?.takeIf { it.proof.isNotEmpty() }?.let {
                Walkthrough(it.proof.map { step -> step.deduction }, it.difficulty,
                    finishes = false, hardestTechnique = it.proof.maxBy { step ->
                        step.deduction.difficulty
                    }.deduction.technique, lessons = it.proof)
            }
        }
        val chosen = tutorTechnique?.let { Techniques.byName(it) }
        if (chosen != null) analysis.findings(chosen.name)
        else route
    }

    /**
     * The tutor's own route, whatever is being browsed on top of it.
     *
     * Separate from [walkthrough] because the picker has to say how long the route is
     * while showing one technique's findings, and it was reading the findings' length -
     * so "Best route" claimed however many places the technique being browsed applied.
     */
    val route: Walkthrough? get() = analysis.route(routeStyle)

    /** How many steps the tutor's own route runs to, whatever is being browsed. */
    val routeLength: Int get() = route?.steps?.size ?: 0

    /** The route grouped into runs of one technique, for the tutor's progress line. */
    val chapters: List<Chapter> by lazy { PuzzleLogic.chapters(walkthrough) }

    /** How many places each technique applies right now, for the tutor's own menu. */
    val findingCounts: Map<String, Int> by lazy { analysis.findingCounts }

    private val computed: Overlay by lazy {
        analysis.overlay(displayKey) {
            PuzzleLogic.overlay(
                grid, overlay, hintStyle, hintDepth, walkthrough, lessonStep, entered, answerShown,
                lessonBefore, practicing,
            )
        }
    }

    /** How many answers this puzzle has, capped. One is the ordinary case. */
    val answerCount: Int by lazy { analysis.answerCount }

    private val displayKey: PuzzleDisplayKey by lazy {
        PuzzleDisplayKey(overlay, hintStyle, hintDepth, lessonStep, tutorTechnique, routeStyle,
            tutorHintProof, answerShown, entered, lessonBefore, practicing)
    }

    private val practiceAlternatives: List<Deduction> by lazy {
        val current = walkthrough ?: return@lazy emptyList()
        analysis.alternatives(displayKey) {
            TechniqueSolver.alternativesAt(grid, current, lessonStep - 1)
        }
    }

    /** Call on a worker dispatcher before handing this immutable state to Compose. */
    fun prepareForDisplay(): PuzzleState {
        hint
        status
        reasoningNote
        liveNote
        openQuestions
        readingHeadline
        progress
        route
        walkthrough
        chapters
        findingCounts
        answerCount
        guidance
        computed
        evidenceLabel
        if (practicing) practiceAlternatives
        return this
    }

    fun overlayDigits(): Map<Int, OverlayDigit> = computed.digits

    fun evidenceCells(): Set<Int> = computed.evidence

    fun focusCell(): Int? = computed.focus

    /** The forcing chain being walked, when the step showing is one. */
    fun chain(): Chain? = computed.chain
    fun candidateMarks(): Map<Int, List<Int>> = computed.candidates
    fun removedCandidates(): Map<Int, Set<Int>> = computed.removed

    val currentDeduction: Deduction? get() =
        PuzzleLogic.stepIndex(lessonStep, walkthrough)?.let { walkthrough?.steps?.get(it) }

    val practicing: Boolean get() = overlay == OverlayMode.LESSON && practice &&
        !practiceRevealed && currentDeduction?.hasTeachingProof == true

    fun practiceAnswer(digit: Int): PuzzleState {
        val step = currentDeduction ?: return this
        val cell = practiceCell ?: return copy(practiceFeedback = "Tap a square on the grid first.")
        val route = walkthrough ?: return this
        val before = route.lessons.getOrNull(lessonStep - 1)?.before.orEmpty()
        var result = PuzzleLogic.practiceAnswer(step, cell, digit, before)
        if (!result.correct && digit in before[cell].orEmpty()) {
            result = PuzzleLogic.practiceAnswer(step, cell, digit, before,
                practiceAlternatives)
        }
        return copy(practiceRevealed = result.correct, practiceFeedback = result.message,
            lessonBefore = false)
    }

    fun showHintProof(): PuzzleState = copy(overlay = OverlayMode.LESSON,
        tutorHintProof = true, tutorTechnique = null, lessonStep = 1,
        lessonBefore = true, practice = false, practiceCell = null,
        practiceFeedback = null, returnToStep = null, selectedCell = null)

    fun visitReason(step: Int): PuzzleState = stepTo(step).copy(returnToStep = lessonStep)

    /**
     * Turning a layer on, or - for a hint - pushing the one already showing one step
     * further.
     *
     * Pressing Hint again is what walks down the staircase, so that asking for more help
     * needs no second control and no explanation of where to find it. Once there is
     * nothing left to reveal, the explanation stays open until explicitly closed.
     */
    fun show(mode: OverlayMode): PuzzleState {
        // Solve pressed again on a puzzle with several answers steps through them instead
        // of putting the layer away. Every other button toggles, and this one would too if
        // there were only ever one answer to show.
        if (overlay == OverlayMode.SOLUTION && mode == OverlayMode.SOLUTION) {
            PuzzleLogic.steppedAnswer(overlay, mode, answerShown, answerCount)?.let { next ->
                return copy(answerShown = next, selectedCell = null)
            }
        }
        val next = PuzzleLogic.press(overlay, mode, hintDepth, hintStyle)
        return copy(
            overlay = next.mode,
            hintDepth = if (next.mode == OverlayMode.HINT && hintStyle == HintStyle.EXPLAIN &&
                hint is Hint.Reveal) PuzzleLogic.HINT_DEPTHS - 1 else next.hintDepth,
            lessonStep = if (next.mode == overlay) lessonStep else 0,
            selectedCell = null,
        )
    }

    /**
     * Putting whatever layer is showing away, outright.
     *
     * Not the same as pressing its button again: pressing Hint walks further down the
     * staircase, which is right for a press of Hint and wrong for a press of Back. Back
     * means undo the last thing that happened on this screen, and the last thing that
     * happened was the layer appearing.
     */
    fun close(): PuzzleState = copy(
        overlay = OverlayMode.NONE,
        hintDepth = 0,
        selectedCell = null,
    )

    /**
     * Starting the tutor on one technique the user picked, from the beginning of it.
     *
     * A different list to walk, so the position in the old one means nothing.
     */
    fun tutor(technique: String? = null): PuzzleState = copy(
        tutorTechnique = technique,
        overlay = OverlayMode.LESSON,
        lessonStep = 0,
        selectedCell = null,
        tutorHintProof = false,
        lessonBefore = false,
        practiceRevealed = false,
        practiceCell = null,
        practiceFeedback = null,
        returnToStep = null,
    )

    /**
     * Opening the tutor again on whatever it was walking, where it was.
     *
     * Shutting it used to forget the position, so a look at the grid halfway through a
     * sixty-step route cost the whole route. Closing is not the same as finishing.
     */
    fun reopenTutor(): PuzzleState = copy(overlay = OverlayMode.LESSON, selectedCell = null)

    /**
     * Moving through the walkthrough. Clamped, so the ends are simply inert.
     *
     * Zero is the introduction and the route's own steps run from one, so the last
     * position is the number of steps rather than one less than it.
     */
    fun stepTo(step: Int): PuzzleState = copy(
        lessonStep = step.coerceIn(0, PuzzleLogic.lastStep(walkthrough)),
        selectedCell = null,
        lessonBefore = false,
        practiceRevealed = false,
        practiceCell = null,
        practiceFeedback = null,
        returnToStep = null,
    )

    fun withCell(index: Int, digit: Int?, source: CellSource): PuzzleState {
        val cell = when {
            digit == null -> Cell.Empty
            source == CellSource.GIVEN -> Cell.given(digit)
            else -> Cell.guess(digit)
        }
        val nextGrid = grid.with(index, cell)
        val readingCell = originalReports?.getOrNull(index) != null
        val corrections = if (readingCell && grid[index] != cell) {
            readingCorrections + ReadingCorrection(index, grid[index].digit, cell.digit,
                grid[index].source.name, cell.source.name, System.currentTimeMillis())
        } else readingCorrections
        return copy(
            grid = nextGrid,
            readingCorrections = corrections,
            uncertainCells = uncertainCells - index,
            // A square the user has answered is theirs now, and is drawn as theirs.
            // Clearing it hands it back.
            entered = if (digit == null) entered - index else entered + index,
            // The route and the hint were both worked out from a grid that has just
            // changed underneath them.
            hintDepth = 0,
            lessonStep = 0,
            tutorHintProof = false,
            lessonBefore = false,
            practiceRevealed = false,
            practiceCell = null,
            practiceFeedback = null,
            returnToStep = null,
            // The reader's account of this square is now out of date - the user has just
            // overruled it - so it goes. Keeping it left the reading layer colouring the
            // square as whatever it had been read as, after being told it is empty.
            reports = reports?.mapIndexed { i, report -> if (i == index) null else report },
        )
    }

    /** The user has looked at everything the reader flagged and is happy with it. */
    fun acceptReading(): PuzzleState = copy(uncertainCells = emptySet())
}

/** Only inputs used by derived display data; selection and feedback do not affect it. */
private data class PuzzleDisplayKey(
    val overlay: OverlayMode,
    val hintStyle: HintStyle,
    val hintDepth: Int,
    val lessonStep: Int,
    val technique: String?,
    val routeStyle: RouteStyle,
    val hintProof: Boolean,
    val answerShown: Int,
    val entered: Set<Int>,
    val before: Boolean,
    val conceal: Boolean,
)

/** Completed results only: a slow solve never holds the cache's monitor. */
internal class PuzzleAnalysisCache<K, V>(private val capacity: Int) {
    private data class Entry<V>(val value: V)
    private val entries = LinkedHashMap<K, Entry<V>>(capacity, 0.75f, true)

    init {
        require(capacity > 0)
    }

    fun get(key: K, compute: () -> V): V {
        synchronized(entries) { entries[key]?.let { return it.value } }
        val value = compute()
        return synchronized(entries) {
            entries[key]?.let { return@synchronized it.value }
            entries[key] = Entry(value)
            if (entries.size > capacity) entries.remove(entries.keys.first())
            value
        }
    }
}

private object PuzzleAnalyses {
    private val cache = PuzzleAnalysisCache<Grid, PuzzleAnalysis>(4)
    fun of(grid: Grid): PuzzleAnalysis = cache.get(grid) { PuzzleAnalysis(grid) }
}

private class PuzzleAnalysis(private val grid: Grid) {
    val status: Status? by lazy { PuzzleLogic.status(grid) }
    val reasoningNote: String? by lazy { PuzzleLogic.reasoningNote(grid) }
    val findingCounts: Map<String, Int> by lazy { TechniqueSolver.findingCounts(grid) }
    val answerCount: Int by lazy {
        when (Solver.solve(grid)) {
            is SolveResult.Unique -> 1
            is SolveResult.None -> 0
            is SolveResult.Multiple -> Solver.solutions(grid, PuzzleLogic.MOST_ANSWERS_OFFERED).size
        }
    }
    private val hints = PuzzleAnalysisCache<HintStyle, Hint?>(2)
    private val routes = PuzzleAnalysisCache<RouteStyle, Walkthrough?>(2)
    private val findings = PuzzleAnalysisCache<String, Walkthrough?>(Techniques.all.size)
    private val overlays = PuzzleAnalysisCache<PuzzleDisplayKey, Overlay>(32)
    private val guidance = PuzzleAnalysisCache<PuzzleDisplayKey, Guidance?>(32)
    private val labels = PuzzleAnalysisCache<PuzzleDisplayKey, String?>(32)
    private val alternatives = PuzzleAnalysisCache<PuzzleDisplayKey, List<Deduction>>(8)
    private val readingNotes = PuzzleAnalysisCache<Pair<String?, String?>, String?>(4)
    private val questions = PuzzleAnalysisCache<Pair<Set<Int>, List<CellReport?>?>, Set<Int>>(4)
    private val headlines = PuzzleAnalysisCache<Int, String>(4)

    fun hint(style: HintStyle): Hint? = hints.get(style) { PuzzleLogic.hint(grid, style) }
    fun route(style: RouteStyle): Walkthrough? = routes.get(style) {
        TechniqueSolver.walkthrough(grid, style)
    }
    fun findings(technique: String): Walkthrough? = findings.get(technique) {
        Techniques.byName(technique)?.let { TechniqueSolver.findings(grid, it) }
    }
    fun overlay(key: PuzzleDisplayKey, compute: () -> Overlay): Overlay = overlays.get(key, compute)
    fun guidance(key: PuzzleDisplayKey, compute: () -> Guidance?): Guidance? = guidance.get(key, compute)
    fun evidenceLabel(key: PuzzleDisplayKey, compute: () -> String?): String? = labels.get(key, compute)
    fun alternatives(key: PuzzleDisplayKey, compute: () -> List<Deduction>): List<Deduction> =
        alternatives.get(key, compute)
    fun readingNote(framing: String?, complaint: String?): String? =
        readingNotes.get(framing to complaint) { PuzzleLogic.readingNote(framing, complaint, grid) }
    fun openQuestions(flagged: Set<Int>, reports: List<CellReport?>?): Set<Int> =
        questions.get(flagged to reports) { PuzzleLogic.stillInQuestion(flagged, reports, grid) }
    fun readingHeadline(count: Int): String =
        headlines.get(count) { PuzzleLogic.readingHeadline(count, grid) }
}
