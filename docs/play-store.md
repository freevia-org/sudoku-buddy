# Getting Sudoku Buddy onto Google Play

The store-description text below is a draft. Review it against the release build, published
privacy policy and final Play Data safety answers before saving or submitting it.

Current status (10 October 2026): the app record, listing, privacy URL, declarations and
IARC rating were previously completed per the release handoff. The release-preparation
branch now integrates the report intake; saved answers and the live privacy page must be
reconciled with the final signed app before submission. Use
[the final-build checklist](play-release-readiness.md) for remaining work and blockers.
The notes below are historical preparation references, not authorization to upload or publish.

This reference is updated to the release-preparation state on 10 October 2026.

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

## Remaining account-owner steps

The Play account, app record, listing, Play App Signing enrollment and IARC rating
already exist. Before Play submission, merge the reviewed candidate workflow to `main`,
dispatch a signed candidate with a version code above every uploaded code, install and
qualify that exact candidate, then publish the matching privacy policy and reconcile the
saved Data safety answers. Complete internal testing and Play delivery checks before
starting review. The detailed gates are in [`play-release-readiness.md`](play-release-readiness.md).

## Store listing assets still to make

| Asset | Requirement | State |
| --- | --- | --- |
| App icon | 512x512 PNG | `docs/store/icon-512.png` |
| Feature graphic | 1024x500 PNG | `docs/store/feature-graphic-1024x500.png` |
| Phone screenshots | at least 2, 16:9 or 9:16, min 320px | five physical-phone screenshots prepared in the release package |
| Title | 30 characters | "Sudoku Buddy" |
| Short description | 80 characters | draft below |
| Full description | 4000 characters | draft below |

The five screenshots were captured on a physical phone and cover scanning, recognition,
checking handwritten answers, hints, and on-device history.

### Short description

> Scan and check paper Sudoku, with clear hints when you get stuck

### Full description

> Sudoku Buddy reads a printed sudoku through your camera and then teaches you how to solve
> it, one step at a time, in plain language.
>
> Photograph a puzzle from a newspaper or a book. The app finds the grid, reads the printed
> clues, and hands you a board you can work on. Your own pencilled answers are read too, so
> a puzzle you have already started carries on where you left it.
>
> The tutor works through twenty-three human solving techniques, from naked singles to
> forcing chains, and names the one it is using at every step. It shows you which squares
> the deduction rests on rather than simply filling a number in, so the point is to
> understand the move rather than to be given it.
>
> Processing and puzzle history stay on your phone. If you choose to submit an uncertain
> reading, its puzzle photo and recognition results can be sent to Freevia for private review
> to improve recognition. Automatic submission is optional and off unless you enable it.
> There is no account, routine usage analytics, ad tracking or advertising. Optional puzzle
> reports are analyzed to improve recognition as described in the privacy policy.

## Deciding before the first upload

- **Minification is off.** Turning R8 on would shrink the Kotlin code, but the app's size
  is dominated by OpenCV's native libraries, which R8 does not touch. The gain is small and
  the risk is a reflection-related crash that only appears in release. Recommended: leave
  it off for the first release, and revisit if size matters.
- **The bundle carries all four ABIs** and is about 67 MB as a file, while each phone
  downloads only its own slice. This is the right shape for Play, and different from the
  arm64-only APK used for Firebase distribution — that one exists to keep test downloads
  small.
- **A closed test track first.** Play now expects a period of closed testing before a
  personal developer account can go to production. Starting that track early runs the
  clock down while the listing is finished.
