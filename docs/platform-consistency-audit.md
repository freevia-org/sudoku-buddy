# Android / iOS consistency audit

19 October 2026. Android is the reference. The actual importers now produce
**identical outputs on all 118 corpus images**, including reading-review markers.
Both new digit errors are fixed. Full-device and native-system-UI equivalence are
separate from this measured recognition result.
This audit compares source contracts, automated workflows and screenshots; it is
not a certification of every screen, device or possible puzzle. Earlier iOS
feature-coverage reports did not establish interaction or visual equivalence.
The independently implemented two-stage iOS hint was a real porting omission.

## Changes made during the audit

The original Android `PuzzleLogic`, `VisualHint`, `Reading` and `PuzzleLayout`
now live in `core:presentation`, which both apps use. The iOS framework exports
actual shared overlays, captions, candidates, proof branches, layer toggles,
ambiguous-answer cycling, diagnostics, history summaries and legends. Tutor
practice uses the same answer rule, including alternative valid moves.

The SwiftUI app now has Where / Square / Trace / Answer, the quarter-filled Hint
button, direct stage selection, repeated-press advancement and persistent proof
position. Proof navigation includes earlier evidence, Before/After, assumptions,
contradictions, rejection and return to the destination. Tutor routes now include
introductions, chapters, actual before/after overlays and practice below the
puzzle. The board uses shared photo coordinates and Android's paper/evidence
hierarchy. Read/Check/Solve toggles and read-only solution cycling are shared.

Other corrections cover cell editing, original versus corrected readings,
photo correction provenance, history summaries/grouping, missing About sections,
settings row taps, explicit automatic-sharing confirmation/cancellation and
revision-aware submission actions. Storage remains backward compatible; normal
user history was not erased. Test sessions use separate history directories.

## Comparison inventory

| Area | Evidence and conclusion |
| --- | --- |
| Puzzle board | Portrait/landscape, manual/photo coordinates, editing and RTL exercised. Screenshots inspected. Native rendering still has the differences below. |
| Read/correct | Photo import, orientation metadata, printed/written/empty changes, review and restart covered by iOS workflows and shared reading tests. |
| Check | Shared grading/evidence and toggle rules; wrong/right entries and unchanged saved progress exercised. |
| Hint | Four stages, repeat presses, direct selection, close/reopen and restoration at Trace/Answer passed. |
| Hint proof | Complex forcing-chain assumptions, contradiction, rejection, earlier evidence and restart exercised. Shared overlay snapshots are tested on JVM and iOS. |
| Solve | Shared unique toggle, ambiguous cycling and impossible-puzzle diagnostics. Solution display does not write answers into progress. |
| Tutor | Introduction, route selection, chapters, Before/After, practice and position restoration exercised; native layout differences remain. |
| History | Current puzzle and progress after restart, groups, summaries, photo review, refused-photo deletion and receipts exercised. |
| Scanning | Capture decisions and sampling are now shared; controls and native camera navigation passed simulator rechecks. Actual native importer replay matches iOS on all 118 images, using the local emulator capability workaround described below. Real-device optics remain unqualified. |
| Preferences | Defaults compared in source; switches, persistence, reveal-only hint and automatic-sharing consent/cancel exercised. |
| Languages/assets | 71 catalogs validated; Persian platform wording corrected; Roboto puzzle faces bundled. French restart, Bulgarian messages and Arabic board order exercised. Every language has not been visually reviewed. |
| Feedback | Mock-service submission, explicit consent, receipts, revisions, restart and deletion covered. No real report was submitted. |
| Bug/diagnostics | Optional diagnostics default off, reviewable public draft and refused-photo local deletion checked. GitHub submission was not performed. |
| About/privacy | Missing explanatory sections added. System navigation and platform notices differ appropriately. |
| Accessibility | VoiceOver cell descriptions include teaching/evidence information. Largest accessibility text scale exercised and screenshot inspected: board and pinned actions remain reachable, stage row scrolls; header truncates. No physical-device VoiceOver audit. |
| Release | Unsigned iOS archive built and bundle inspected; Android Debug build/unit/instrumentation tests passed. Signing and distribution remain unavailable. |

## Remaining differences and verification limits

1. **Camera integration:** the missing history/Auto photo/overflow controls,
   Android-style framing brackets and shutter, retry action and return navigation
   have now been implemented. The live preview is exercised with a Debug-only
   unavailable-camera fixture, including return from Settings; optics and real autofocus remain unqualified.
2. **Capture policy:** the separate iOS dwell/brightness/shape policy has been
   removed. `core:image/CapturePolicy` now owns Android's five-frame stability,
   scaled drift, two-miss forgiveness, grid-region darkness/glare, structural
   limits and relative/quadrant-focus rules. Android delegates to it; iOS calls
   it through a queue-owned Kotlin bridge. Portable photometry is checked against
   Android OpenCV, including quadrant borders. Metering APIs remain native.
3. **Recognition:** the shared pipeline now owns sampling policy, integer luma,
   rule alignment, final perspective/surface pixels, grid-line profiles, glyph masks,
   area reduction and the reader. Native geometry uses bit-exact detector resizing
   and bundled codecs. The iOS Vision fallback is removed. All 118 actual importer
   outputs match for status, clues, progress and review markers. Correct digit-and-role
   cells are 3299/3402 and nonempty digits 2161/2187 on both platforms. The trained-on
   corpus is regression evidence, not independent generalization or proof of perfect OCR.
4. **Visual details:** contradiction digits are stroked, elimination arrows use
   a shared causal-unit rule, arrows clear teaching glyphs, tutor duplication was
   removed, and Before/After, move effects and dependency returns were aligned.
   App-owned text, the grid and proof now use Android's Roboto faces. Native sheets, menus,
   pickers, sharing and system navigation still render differently. Pixel-for-pixel equivalence is not established on every device.
5. **Entry points/version:** the extra Hint proof menu shortcut was removed;
   manual reset/example tools are limited to isolated Debug test sessions.
   The release marketing version is aligned to Android's 1.0.1. No release was
   published. All four orientations are explicitly supported on phone and tablet.
6. **Android scan verification:** a minimal 32×32 warp isolated the emulator's
   SIGILL to native SVE instructions. Apple HVF advertises SVE2 without usable SVE;
   an opt-in Debug wrapper masks that impossible flag. Native identity, real importer
   and full-corpus tests then pass. The wrapper is absent from normal/Release builds,
   and the Gradle opt-in rejects Release requests. Production camera features were
   not disabled to work around the emulator.
7. **Hardware/release:** No physical iPhone is available. Camera optics, sustained
   performance, real-device accessibility and distribution are unqualified.

## Current verification

- `exact-final-platform-comparison.json`: all 118 actual importer outputs match.
- `exact-final-ios-corpus.json` and `exact-final-android-corpus.json`: complete
  inventories, 117 reviewable images and the same expected refusal. The two new
  ambiguous-screen digit errors read 7 and 5 on both platforms.
- `exact-final-recognition-tests.log`: full recognition and vision tests pass,
  including the narrow detached-crossbar recovery, with unchanged model weights.
- `aligned-platform-contracts.log`: shared image/presentation/bridge and Android
  unit contracts pass on JVM and native iOS. New tests cover source luma, final
  perspective/surface pixels, rule alignment and the NSData boundary.
- `exact-final-android-ui.log`: both final Compose workflows pass with clean
  screenshots in `android-exact-final-screens/`.
- `SubmissionParityUI.xcresult`: **22/22 workflows passed in one complete run** on the final UI/storage source (946 seconds), including expanded/collapsed photo review after restart.
- `SubmissionIPadUI.xcresult`: **3/3 workflows passed** on the 13-inch iPad: portrait/landscape, imported photo entry and four-stage photo hints. Direct Device Hub landscape verification confirms the full grid, wrapped guidance and all controls.
- `submission-release.log` and `submission-release-audit.json`: final unsigned device archive and audit pass; 71 catalogs, four Roboto faces, matching model, production reporting endpoint, privacy declarations and no Debug entry points.
- `submission-storage-tests.log`: **36 persistence/report tests pass**. Expanded photo review is valid independently of manual clue editing; both expanded and collapsed states survive restart.
  Framing advice survives restart; malformed optional advice cannot hide a puzzle.
  Report grid lines now use Android's normalized coordinates, including legacy iOS data.
- `submission-resource-tests.log`: **14 resource/replay guards pass**: catalogs, all 71 platform-brand adaptations,
  bundled font provenance/registration inputs and replay-comparison guards pass.

Physical iPhone camera behavior, real-device performance/accessibility and Apple
Developer signing/distribution remain unavailable. They require the hardware and
account access the user does not currently have. No report, issue, corpus upload,
commit or publication was made during this work.

## Submission packet

[App Store materials](app-store/README.md) include actual iPhone/iPad screenshots, listing copy, reviewer notes and a synthetic sample, privacy answers, a cross-platform policy source update, website publication draft and hardware/team gates. Nothing was uploaded or published. All fourteen store image RGB arrays are verified unchanged after opaque PNG encoding. Normal launch through the real photo picker, Read, saved history, restart and four-stage hints were also verified without test launch arguments. The handoff device is **Sudoku Buddy iPhone**; the prior demo simulator is no longer present, so its former history is not claimed restored.

## Historical evidence retained locally

The following earlier runs are retained for traceability; their recognition gaps
and partial UI coverage are superseded by the current checks above.


Build logs, result bundles and screenshots are under ignored `.tools/`:

- `whole-app-android-and-shared-tests.log`: Android unit/shared tests and two
  Compose instrumentation workflows passed.
- `final-platform-contracts.log`: latest Android assemble/unit tests and 47
  presentation plus 16 framework-boundary tests passed on JVM and native iOS.
- `parity-storage-final.log`: 38 Swift persistence/report/capture tests passed.
- `whole-app-native-geometry.log` and `native-geometry-corpus.json`: all 118
  images located by native geometry; this measures geometry, not digit accuracy.
- `final-consistency-corpus.json`: fresh actual iOS importer replay completed all
  118 images (111 originals, 7 rectified). 117 opened for review; the known result
  screen was correctly refused. Across 42 labeled images, 3297/3402 cells had
  both correct digit and role (96.9%); 2157/2187 nonempty digits were correct.
  This matches the prior baseline. The corpus overlaps training and does not
  measure independent generalization or physical-camera equivalence.
- `FullPlatformParityUI-v2.xcresult`: 16/18 workflows passed in the broad run;
  remaining scenarios were corrected/rechecked in focused result bundles.
- `FinalParityVerification.xcresult`: automatic sharing/reveal-only settings
  passed; refused-draft and impossible-puzzle assertions required follow-up.
- `FinalEdgeVerification.xcresult`: actual maximum accessibility text scale and
  ambiguous/impossible Solve passed. Refused-photo confirmation needed an explicit
  alert; `FinalDeletionVerification.xcresult` then passed deletion/restart and
  the optional-diagnostics public-draft workflow. All 19 current iOS workflow
  scenarios have passing evidence across the broad run and focused reruns, not
  a single all-green 19-test run. `final-ui-verification-inventory.json` maps each
  current workflow to its passing log.
- `consistency-screens/`, `final-edge-screens/` and `android-reference-latest/`:
  comparison captures. The updated Debug app was installed over the user
  simulator without erasing its history; Device Hub visibly shows all four stages.
- `final-consistency-release.log`: final unsigned archive succeeded.
  `final-release-audit.json`: 71 catalogs, iOS 16 minimum, no bundled debug
  photos or fixture flags, no microphone permission, unsigned as expected.
- `parity-i18n-final.log`: 31 catalog-tool tests; resource/replay tool tests,
  release-tool tests and 25 feedback-backend mock tests also passed.

No real feedback report, GitHub issue, corpus upload, commit or publication was
made as part of those earlier audit passes. Current corpus equivalence is recorded
above; hardware and native-system-UI limits still apply.

## Continuation evidence

- `camera-android-parity.log`: original Android advisor and structural tests,
  portable/OpenCV photometry equivalence and Android build/unit tests passed.
- `parity-contracts-v3.log`: seven common capture-policy/photometry tests,
  52 shared presentation/sampling tests and 16 framework boundary tests passed;
  native iOS additionally passed the stateful NSData camera-bridge test.
- `camera-geometry-v2.log`: native blank/rectangle rejection and accepted-grid
  framing snapshots (including grid-region luminance) passed.
- `parity-followup-storage.log`: 34 Swift storage/diagnostic tests passed; four
  obsolete independent Swift dwell-policy tests were replaced by shared tests.
- `parity-followup-android-final.log` and `parity-continuation-android-ui.log`:
  final Android build/unit tests and both Compose UI workflows passed. Some
  newly exported Android screenshots contain an emulator System UI ANR overlay
  and are excluded from visual evidence; the clean earlier captures remain the
  reference for those unchanged screen states.
- `parity-followup-i18n-final.log` and `parity-followup-resources-final.log`:
  31 catalog-tool tests and eight resource/replay tests passed.
- `ParityContinuationUI.xcresult`: 19/20 workflows passed; the new camera-row
  tap assertion found a real interaction issue, corrected in the follow-up.
- `ParityFinalChangesUI.xcresult`: final proof-chain, largest text and photo tests
  passed; camera tap/duplicate accessibility findings led to final corrections.
  `CameraInteractionParityUI.xcresult` subsequently passed the full-row Auto photo,
  persistence, fallback history/settings navigation and phone rotation checks.
  `NativeCameraTutorParityUI.xcresult` passed the final full-screen camera/settings
  return and tutor workflows after removing duplicate underlying controls.
- `parity-continuation-ui-inventory.json`: all 21 current workflows have passing
  evidence across the broad run and focused reruns, not one all-green final run.
- `native-camera-tutor-screens/`: final camera and tutor screenshots inspected;
  the tutor's answer remains on white paper, with the Android hierarchy and
  Before/After controls. Native typefaces and switches remain platform-specific.
- `parity-reviewed-release.log`: final unsigned 1.0.1 archive passed. Its bundle
  audit confirms all four phone/tablet orientations, 71 catalogs, the Release
  feedback endpoint and exclusion of Debug fixtures/entry flags. No signing or
  distribution took place.

- `parity-reviewed-corpus.json`: the final actual importer replay completed all
  118 images. 117 opened for review; the known annotated result screen was
  correctly refused. On the 42 labeled images, 3295/3402 digit-and-role cells
  were correct (96.85%; 96.9% rounded), versus 3297/3402 before the sampling
  alignment. Nonempty digit accuracy is 2155/2187. The only changed photograph
  is `sudoku-buddy-2026-09-05-screen-ambiguous.jpg`: 67/81 correct cells versus
  69/81 previously. The Android-resolution alignment therefore carries a
  two-cell measured regression, not an accuracy improvement. No filename-specific
  rule or ground-truth-driven alternate-result selection was added. Manual
  review remains necessary; no claim of identical recognition is made.

The original user simulator was updated in place and visually checked in Device
Hub. Its four-stage hint and puzzle history remain present. The temporary Android
emulator was stopped; the separate iOS review simulator is shut down after replay.
