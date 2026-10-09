<p align="center"><img src="docs/images/app-icon.png" alt="" width="72" height="72"></p>

# Sudoku Buddy

**The joy is in figuring it out.**

Keep your pencil. Keep your puzzle. Get a little help when you need it.

Sudoku Buddy is an Android companion for standard 9×9 Sudoku on paper. Scan a
puzzle—including the answers you have already written—check the reading, and
ask for a hint that explains the next move. It is made to help with the puzzle
already on your page, not replace it.

[Website](https://freevia.org/sudoku-buddy/) ·
[Usage guide](docs/user-guide.md) ·
[Support](.github/SUPPORT.md) ·
[Privacy](https://freevia.org/sudoku-buddy/privacy)

## See the app

<p align="center">
  <img src="docs/images/01-scan.webp" width="205" alt="Camera framing a paper Sudoku with handwritten progress">
  <img src="docs/images/03-check.webp" width="205" alt="Checking handwritten answers on a scanned puzzle">
  <img src="docs/images/04-hint.webp" width="205" alt="A hint highlighting the cells behind a deduction">
</p>

These are real Android screens from a development build. The interface may
change before release. [See more on the website](https://freevia.org/sudoku-buddy/#screenshots).

## Get the app

**Google Play — Android:** The first release is in preparation, but there is
no public download yet. It is planned to be free for Android 8.0+ phones with
a camera. This repository is not a Play-approved release or an invitation to
install an unverified build.

## What it helps you do

- Read printed clues and handwritten progress, then correct a misread square.
- Check your answers and reveal a hint in stages, with the relevant cells and
  solving technique highlighted.
- Reopen saved puzzles from on-device history and continue after a break.

Scanning, checking and hints work on the phone, including offline. Handwriting,
erasures and lighting can cause misreads, so review the recognized grid before
relying on a check or hint.

## Support, feedback and bugs

[Get support](.github/SUPPORT.md) ·
[Share feedback](https://github.com/freevia-org/sudoku-buddy/issues/new?title=Suggestion%3A%20) ·
[Report a bug](https://github.com/freevia-org/sudoku-buddy/issues/new?title=Bug%3A%20)

GitHub issues and attachments are public. Remove personal details from reports
and use the private contact option in the support guide for a photograph or
example you do not want to post publicly.

## Privacy and release status

[Privacy policy](https://freevia.org/sudoku-buddy/privacy) ·
[Local-data deletion](https://freevia.org/sudoku-buddy/privacy#uninstalling)

The public policy describes the earlier offline-only build. A separate
release-preparation branch contains optional private puzzle-report submission,
but no report-uploading build has been published. Its final behavior, device
test, Play disclosures and matching public policy must be verified before
distribution. The app has no account, ads or analytics.

The repository is publicly viewable; it does not currently include a
top-level license granting reuse rights.

## For contributors

The Android app uses Kotlin, Jetpack Compose, CameraX and OpenCV, with separate
modules for recognition and deterministic solving. Use JDK 21 and Android SDK
platform 36. From the repository root:

```powershell
.\gradlew.bat build
```

On macOS/Linux, use `./gradlew build`. The [usage guide](docs/user-guide.md)
and [Play preparation notes](docs/play-store.md) provide more
detail. A passing local build is not a Play release.
