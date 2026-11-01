# Sudoku Buddy Google Play listing and release notes

The listing copy below reflects the current English store text. Check it against the signed
release, published privacy policy and submitted Play Data Safety answers before making future
listing changes.

Current status (verified 1 November 2026): **1.0.5 (code 244)** is Active on Production
across 178 countries/regions. Google Play Console reports no unpublished changes. Internal
testing also serves 244; Publishing confirmed both tracks use artifact ID
`4860245793654491695`. The Play-delivered build passed the screenshot-share → recognize →
save smoke test on a OnePlus CPH2449. A 16 KB Android 16 emulator run also exercised the
flow using locally re-signed APK splits generated from the candidate AAB. The app supports
71 locales, with model-assisted translation review that remains partial. See [the
final-build checklist](play-release-readiness.md) for detailed release status and
qualification limits.

This is a dated status snapshot. Recheck Play Console before using it for a
later release or listing update; older submission details below are historical.

## Done in the repository

| Item | State |
| --- | --- |
| App bundle | `./gradlew :app:bundleRelease` produces `app/build/outputs/bundle/release/app-release.aab` |
| 16 KB page alignment | `checkNativeAlignment` checks the arm64 APK; the final bundle and 16 KB runtime still require qualification |
| Target API level | 36, Play's requirement for new phone apps from 31 August 2026 |
| Minimum API level | 26 |
| Signing | distribution requires the key in `docs/signing.md`; local builds can still fall back to debug signing |
| Version code | `BUILD_NUMBER`; select a code greater than all previous Play uploads, since separate workflows have separate run numbers |
| Permissions | Release-preparation branch requires `CAMERA` and `INTERNET` for opt-in report submission. Verify the final merged manifest |
| Privacy policy | Live at `https://freevia.org/sudoku-buddy/privacy` with source in `docs/privacy-policy.md` |
| Store icon | `docs/store/icon-512.png` |
| Feature graphic | `docs/store/feature-graphic-1024x500.png` |
| Console answers | `docs/play-console-submission.md` |

## The 16 KB problem, and why it is worth knowing about

Play requires every app with native code to work on devices with 16 KB memory pages. A
library that fails this does not misbehave subtly — it fails to load, on a class of phone
this build was never run on.

OpenCV 4.11.0 shipped a correctly aligned `libopencv_java4.so` next to a stale
`libc++_shared.so` that was still built for 4 KB pages, so the fault sat entirely in a
dependency's packaging. Moving to OpenCV 4.12.0 fixes it. `checkNativeAlignment` reads the
ELF program headers of every library in the built APK and fails the build if any of them
declares a load alignment below 16384, so this cannot come back unnoticed through a future
dependency bump.

Note that the JVM tests link against `org.openpnp:opencv`, a different artifact, so the
test suite does **not** exercise the Android library. The bump is verified for alignment
and for compilation, not for behaviour — that check is a scan on a real phone.

## Next release owner steps

The Play account, app record, listing, Play App Signing enrollment and IARC rating
already exist. For a future update, address the Console's obfuscation and dependency
notices, then build a signed candidate from reviewed `main` with a version code above
every uploaded code. Qualify that exact candidate, reconcile the privacy policy and
Data safety answers, upload it to Internal testing, and verify Play delivery before
promoting the same artifact to Production. The detailed evidence and historical release
record are in [`play-release-readiness.md`](play-release-readiness.md).

## Store listing assets and source copy

| Asset | Requirement | State |
| --- | --- | --- |
| App icon | 512x512 PNG | `docs/store/icon-512.png` |
| Feature graphic | 1024x500 PNG | `docs/store/feature-graphic-1024x500.png` |
| Phone screenshots | at least 2, 16:9 or 9:16, min 320px | five physical-phone screenshots prepared in the release package |
| Title | 30 characters | "Sudoku Buddy" |
| Short description | 80 characters | current public English (US) copy below |
| Full description | 4000 characters | current public English (US) copy below |

The five screenshots were captured on a physical phone and cover scanning, recognition,
checking handwritten answers, hints, and on-device history.

### Short description

```
Scan paper Sudoku, check your answers and get step-by-step hints.
```

### Full description

```
Keep solving on paper. Sudoku Buddy helps you read, check and understand a 9×9 puzzle from a newspaper, book or magazine—without taking away the fun of figuring it out.

SCAN YOUR PAGE
• Point your camera at the grid. Sudoku Buddy reads printed clues and handwritten progress.
• Review the reading and correct missed digits. Lighting, pencil marks and handwriting can affect recognition.

CHECK AND LEARN
• Check the answers you have written and see what needs another look.
• When you are stuck, reveal a hint in stages: region, technique, square and digit.
• Explore the Tutor's explanation, solve the puzzle when you choose, and reopen scans from on-device history.

Scanning, checking and tutoring work offline. Unless you choose to share a report, puzzle photos and history stay on your phone. There is no account, ads, routine usage analytics, crash reporting or ad tracking.

Optional sharing helps improve recognition. You can submit an uncertain reading manually or enable automatic submission for future uncertain readings; automatic sharing is off by default. A report includes the puzzle photo, reading results and corrections. Reports remain private until you delete each revision using its receipt in Settings. Sudoku Buddy does not currently train a model from submitted reports; if it does later, deleting a report may not undo what the model learned. Sharing and report deletion require an internet connection. The app works without sharing. See the privacy policy for details.
```

## Historical first-upload decisions

- **Minification is off.** Turning R8 on would shrink the Kotlin code, but the app's size
  is dominated by OpenCV's native libraries, which R8 does not touch. The gain is small and
  the risk is a reflection-related crash that only appears in release. Recommended: leave
  it off for the first release, and revisit if size matters.
- **The bundle carries all four ABIs** and is about 67 MB as a file, while each phone
  downloads only its own slice. This is the right shape for Play, and different from the
  arm64-only APK that CI also builds — that one is for installing directly on a test phone
  and keeps the download small.
- **A closed test track first.** Play now expects a period of closed testing before a
  personal developer account can go to production. Starting that track early runs the
  clock down while the listing is finished.
