# iOS development

The iOS app uses SwiftUI (iOS 16 and later) and a static Kotlin Multiplatform
framework, `SudokuShared`. Model, recognition, solver, hints, strategies, routes,
and teaching proofs reuse the Android implementation. Camera capture, image
loading, local storage, sharing and navigation use native Apple interfaces.

## Open and run

Open `iosApp/SudokuBuddy.xcodeproj`, choose the `SudokuBuddy` scheme and an iPhone
simulator, then Run. Full Xcode, an installed iOS runtime and JDK 21 are required.
The first build needs network access to download Kotlin/Native and the pinned
OpenCV binary. CocoaPods and Android Studio are unnecessary for iOS development.

The Xcode build phase runs `tools/ios/build-shared.sh`. It selects `JAVA_HOME`, a
project-local `.tools/jdk-21*/Contents/Home`, or an installed JDK 21. Gradle uses
`.tools/gradle` unless `GRADLE_USER_HOME` is supplied. On this Mac, Xcode 27.1 and
its matching arm64 iOS runtime (`24A94232`) are installed.

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
xcodebuild -project iosApp/SudokuBuddy.xcodeproj -scheme SudokuBuddy \
  -configuration Debug -destination 'generic/platform=iOS Simulator' build
```

Simulator tests need normal local signing. Use `CODE_SIGNING_ALLOWED=NO` only
for an unsigned device compilation, not for launching XCTest.

## Scanning

**Scan puzzle** offers a native live camera and Apple's photo picker. Only video
permission is requested; there is no microphone permission or whole-library access.
Denied or unavailable camera access leaves photo import available. A failed scan
preserves the current puzzle. Images honor EXIF orientation and are processed on
the device, including offline.

`iosApp/VisionGeometry` implements the Android `QuadDetector`, `GridLocator`,
`GridScorer` and `CellGrid` approach in Objective-C++: outline candidates at two
scales, grid-line scoring, small contour-growth rescues, long-rule outlines,
cell-lattice homographies, fitted curved-page surfaces and a final occlusion pass.
The 4.12.0 OpenCV XCFramework is pinned by URL and SHA-256 in `Package.swift`.
The detector uses bit-exact resizing; Kotlin now supplies rule alignment and final
perspective/surface sampling as well as the recognizer. Both apps use the bundled
OpenCV photo codecs where supported. The iOS-only Vision rectangle fallback was
removed. A confirmed grid is not rejected again by the preview-only line gate.
The actual importers now match on all 118 corpus images; see the
[full comparison](recognition/ios-corpus.md).

The shared Kotlin reader reuses line fitting, cell extraction, the shipped
423,212-byte CNN model, ink/color/notes classification, uncertainty and solver
acceptance rules. Native preprocessing implements OpenCV's reflected box mean,
opening, connected components and area resize. JVM comparisons test those
operations against OpenCV. Surface-warped pages use grayscale role analysis, as
Android does when a perspective transform cannot reproduce the color geometry.

The camera outlines measured grids and gives framing, lighting and focus advice.
Automatic capture, enabled by default as on Android, requires a visible measured
grid and the same shared five-frame stability/quality policy used by Android.
The preview includes history, an Auto photo switch and the same overflow actions;
automatic capture can also be disabled in Settings. It never submits a report. Actual focus, exposure, shutter timing and orientation behavior still
need a physical iPhone.

Every reading opens for review. Cyan tints identify printed clues and purple
identifies handwriting; the app's read digit is cut out of the tint to preserve
the photograph. Amber marks uncertainty, blue identifies solution or teaching
answers, and red identifies incorrect answers. Manual test boards use black ink
on white paper. The selected cell exposes the original
classifier confidence and runner-up; low confidence is described as a guess.
Corrections retain the original reading evidence. Review before choosing **Start puzzle**
to start play; no recognizer is infallible.

The six most recent refused original captures are retained locally for explicit
sharing or deletion in **Your puzzles**, under **Would not read**. They are never uploaded
automatically. Successful rectified photos are retained with their saved puzzles.

## Puzzle history and teaching

Every edit saves puzzle progress, selected cell, printed/handwritten roles,
uncertainty, review mode, hint reveal state, recognition evidence and corrections.
Backgrounding and restarting restore the current puzzle. Starting a new puzzle
keeps prior history. Deleting a saved puzzle removes its local photo without
removing unrelated history or submission receipts.

Storage uses atomic per-puzzle JSON, separate JPEGs and a current-session pointer
under Application Support. Unreadable or future records are retained; readable
history remains available and the UI reports storage failures.

**Tutor** exposes the shared strategy catalog, examples available on the current
puzzle, a full solving route and a hint proof. Before/After boards show candidate
changes, dependencies and forcing-chain consequences. Hint proofs offer backward
“Why not?” questions and a return stack that preserves the previous evidence frame.
Practice mode hides the
answer until a correct attempt or explicit reveal. An answer is placed in the
actual puzzle only by its explicit action. The route and practice position survive
restart. The full solution is a read-only overlay; it does not fill saved progress.
Settings retain explained/revealed hint style and short-chain route preference.

## Feedback and privacy

Private recognition reports require explicit analysis-and-training consent.
Automatic uncertain-reading sharing defaults off and needs a separate confirmation.
It includes original uncertain scans and subsequent review corrections; ordinary
gameplay answers do not generate training corrections. Disabling the preference
cancels queued automatic work. An unknown in-flight outcome retains its receipt.

The client follows `feedback-api/README.md`: a deterministic ZIP containing
`manifest.json` and `puzzle.jpg`, SHA-256 over the exact archive, an explicit training
consent header, bounded HTTPS requests with redirects disabled, and strict
HTTP/status/receipt validation. The pending receipt and retry archive are durable
before network transmission. A confirmed upload removes the retry archive.

**Submission receipts** survives puzzle deletion and app restarts. Deletion is
journaled before DELETE, so a pending upload cannot resume after a deletion request.
The UI only shows **Removed** after server confirmation. A local tombstone prevents
resubmitting the same revision. Server-retained training copies can be deleted;
a model already trained on them may retain learned changes. Uninstalling removes
local receipts, so delete remote reports first when that is wanted.

Release uses the same endpoint as Android. Debug has no endpoint unless configured
with `SUDOKU_FEEDBACK_ENDPOINT`. Automated tests use `--mock-feedback-service` and
synthetic photos; development never uploads corpus photographs to the real service.

Bug reporting opens a reviewable GitHub browser draft only after its button is
pressed. Optional redacted diagnostics default off. The app does not post issues
or attach photos; the disclosure explains that submitted GitHub content is public.
Native share sheets send only what the user explicitly selects and hands off.

`PrivacyInfo.xcprivacy` declares optional photo, puzzle, support and diagnostic
collection, no tracking, app-local UserDefaults and the native library's required
API reasons. These declarations must be reconciled with the final App Store privacy
answers and published policy before distribution. Apple reference:
[privacy manifest data types](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacycollecteddatatypes/nsprivacycollecteddatatype).

## Localization and assets

All 71 Android message catalogs are bundled natively. **About > Language** persists
a choice or follows the device. Translation occurs at presentation time; stored
puzzles and technique identifiers remain language-neutral. Arabic, Hebrew and
Persian navigation is RTL; the board and keypad preserve row/column geometry.

```sh
python3 tools/ios/prepare-resources.py
python3 tools/ios/test_resources.py
```

`tools/ios/ios-messages.json` maps native wording to reviewed Android equivalents.
The existing Freevia artwork provides the opaque 1024px app icon, in-app mark and
dark launch screen. About includes offline Kotlin, runtime and OpenCV notices.
Controls expose accessibility labels; physical VoiceOver qualification is pending.

## Tests and full-corpus replay

```sh
swift test --package-path iosApp/Persistence
swift test --package-path iosApp/VisionGeometry
./gradlew -PiosOnly=true :shared:jvmTest :shared:iosSimulatorArm64Test
```

Use JDK 21 and the Xcode developer directory described above. Run Gradle commands
sequentially because test processes share compilation outputs. `.github/workflows/ios.yml`
checks native storage, geometry, shared logic, localization, SwiftUI flows and an
unsigned device release. No signing secrets or corpus photos are required in CI.

The real-image corpus stays local. With `corpus`, `corpus-labels` and
`corpus-rectified` in the repository root, build a Debug simulator app, then:

```sh
python3 tools/ios/replay-corpus.py --device YOUR_SIMULATOR_UUID --prepare
# After CorpusReplay/complete exists in the reported sandbox directory:
python3 tools/ios/replay-corpus.py --device YOUR_SIMULATOR_UUID --collect
SUDOKU_CORPUS_ROOT="$PWD" swift test --package-path iosApp/VisionGeometry
```

Replay invokes the actual iOS importer and Kotlin reader. It never transmits images.
Reports include every image and every labelled cell; refusals remain in accuracy
denominators. The classifier was trained on this corpus, so these are regression
scores, not independent generalization estimates. Initial Vision-only results are
preserved in `docs/recognition/ios-vision-baseline.json`; the reproduced Android
baseline is `ios-port-android-baseline.json` in that directory.

## Distribution qualification

Freevia EOOD team `8C782Q826S` now has valid local signing identities and an
App Store distribution profile. Version 1.0.2 (build 1) was exported, uploaded and
submitted on 23 October 2026; App Store Connect showed **Waiting for Review** on
25 October. See [release status](app-store/release-status.md). Physical camera,
VoiceOver and thermal/memory qualification remain unrecorded. Simulator and
corpus results do not establish those checks.

## Verified handoff

See [parity and validation](ios-port-parity.md) and the
[full-corpus report](recognition/ios-corpus.md). The unsigned device archive is
`.tools/SudokuBuddy-unsigned.xcarchive`; it requires the intended development team
before installation/distribution. Its Release service endpoint, 71 locales, shared
model, native framework, privacy manifest and absence of Debug photos were audited.
The final receipt flow and tutor restart/proof flow passed on a standard iPhone
17 Pro simulator (iOS 27.0). This adds simulator qualification, not physical-camera
or signed-distribution qualification.

## App Store packet

See [submission preparation](app-store/README.md) for the final 22-workflow iPhone run, three iPad workflows, 36 storage tests, audited unsigned archive, screenshots, primary listing and review/privacy drafts. Physical-device and intended-team qualification remain required.
