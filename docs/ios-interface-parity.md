# SwiftUI interface alignment with Android

**Follow-up audit:** the earlier layout checks below did not establish complete
interaction parity. The four-stage hint and other differences found afterwards
are tracked in [the platform consistency audit](platform-consistency-audit.md).

The Android reference is `PuzzleScreen.kt`, `PuzzleLayout.kt`, `Ui.kt`,
`HistoryScreen.kt`, `PuzzleDialogs.kt`, and the actual Android store captures in
`docs/store/screenshots`. The iOS implementation uses SwiftUI with native modal
presentation, permissions and photo selection.

## Layout

- A compact app bar contains the puzzle drawer, title and remaining-square count,
  camera, and overflow menu. Strategies, Settings, About and feedback live in the
  overflow rather than occupying permanent toolbar slots.
- The straightened photograph is the puzzle surface. Read distinguishes printed
  clues, handwriting and candidate marks using the Android semantic colors; digit
  cutouts retain the original paper underneath. Confidence bars use the same
  0.67/0.90 thresholds. Check highlights entered answers green/red without painting
  duplicate digits over the original handwriting.
- The explanatory pane scrolls independently. Read, Check, Hint and Solve remain
  pinned, followed by the Tutor handle. The landscape arrangement places the
  photograph beside the controls. Longer button labels can use two rows.
- Tapping a cell opens a correction sheet with Android's 3×3 keypad; it no longer
  permanently takes space below the board. The existing correction evidence, solver, consent,
  reporting and persistence models remain responsible for their behavior.
- Saved puzzles appear in a side drawer, grouped by day with photo thumbnails,
  time, printed/entered counts, sharing and deletion controls.
- Settings exposes the same switch explanations and language choice. Auxiliary
  screens use compact titles and the dark palette. The scanner automatically
  opens live capture on devices with a camera; the simulator offers photo import.

## Puzzle typography

App-owned labels, controls, the board, candidates and proof graphics now use static Roboto faces generated
from the Android reference font, including bold/italic assumptions and outlined
contradiction digits. The faces retain their copyright/license metadata and ship
with attribution. Native system sheets, pickers and navigation remain platform UI.

The shared Kotlin layout calculates the photo/control arrangement on both platforms; iOS supplies its safe-area-adjusted chrome height. Review availability and visibility also use shared policy. Confident photos enter the puzzle directly, original review questions keep the Review reading shortcut available, selecting a cell collapses the banner, and visual hints hide the normal legend.

## Grid sizing

The former nested stack used the intrinsic dimensions of text labels and empty
buttons while negotiating square cells. This left unused width inside the board.
The puzzle now positions all 81 cells using explicit rectangles in the board's
coordinate space. Photographs use the detected grid-line positions; manual grids
use exact ninths. The tutor board also assigns explicit equal cell dimensions.
No digit, empty label, or button style determines a cell's size.

## Verification

The UI regression suite captures empty/manual entry, photograph reading,
correction, Check, Hint, Solve, landscape, the history drawer, Settings and the
Tutor route. It checks square cells, complete nine-column coverage, reachable
edge cells, correction restoration, and solution/progress independence. Reporting
checks use synthetic imagery and a local mock service; no corpus photo is sent.

### Final submission verification, 19 October 2026

`SubmissionParityUI.xcresult` passed all 22 workflows in one run after the typography, shared review policy and persistence-validation fixes. `SubmissionIPadUI.xcresult` passed all three tablet workflows. The selected iPhone/iPad store captures were visually inspected; direct Device Hub checks verified iPad landscape without relying on XCTest orientation metadata.

### Earlier handoff evidence

Eight distinct UI workflows passed across the focused runs on an iPhone 17 Pro
simulator with iOS 27.0. The final source built and passed all three handoff tests:

- `AndroidParityUI-Handoff.xcresult`: portrait/landscape geometry, editing, Check,
  staged and revealed Hint, read-only Solve, drawer, Settings; photo overlays,
  3×3 keypad geometry, visible confirmation, correction/restart, reopening review
  after confirmation, scanner import; explicit consent, receipt/restart and
  confirmed deletion/restart. All three tests passed.
- `AndroidParityUI-Polished.xcresult`: four tests passed, including the Arabic
  drawer opening on the right while the Sudoku coordinates remain left to right.
- `AndroidParityUI-Regression.xcresult`: all six tests passed for Arabic, staged
  hints, French localization/offline licenses, history/restoration/deletion,
  consent/receipts/deletion, and tutor route/proof/restoration. Both puzzle and
  tutor boards have measured full nine-column coverage.
- Eight resource/replay tooling tests passed. All newly used literal messages
  exist in the generated English catalog. `git diff --check` passed.

Results and unmodified screen captures are local, ignored artifacts under
`.tools/`; the final captured screens are in `android-parity-handoff-screens`.
The Android references were inspected alongside the rendered photo review,
correction keypad, Check/Hint/Solve, history, Settings, tutor, About and consent
screens. This simulator runtime crops some `XCUIApplication.screenshot()` landscape
attachments despite reporting correct landscape geometry. The actual Device Hub
window was therefore rotated and inspected directly: the entire square board and
all four actions are visible side by side. It was returned to portrait afterward.

The verified build is installed and running on the original
`Sudoku Buddy iPhone Verification` simulator, with its existing puzzle restored.
Device Hub is open at Zoom to Fit. The temporary UI-review simulator was shut
down. No real report was sent and no saved user data was erased. Live camera
qualification still requires a physical iPhone.
