# iOS submission preparation

Prepared 19 October 2026. No Apple account, signed upload, App Store record, public report submission or App Store publication was used. This packet separates local preparation from final owner/hardware gates.

**Update, 26 October:** signing, the App Store record, upload and review submission
have since completed. Version 1.0.2 (1) was rejected; the developer's Resolution Center
response was sent and received, but the submission remains unresolved. See
[current release status](release-status.md) before following the original
preparation steps below.

## Materials

- `en-US/`: complete primary English listing text (name, subtitle, keywords, promotion, description, release notes). Additional store translations are optional; the app itself includes 71 language catalogs.
- `submission-answers.md`: app record, categories, age/encryption assessment and remaining owner-only entries.
- `privacy-answers.md`: data-flow inventory and draft privacy-label answers, mapped to the bundled manifest.
- `review-notes.txt`, `review-puzzle.png`, `review-puzzle.txt`: reviewer instructions and a synthetic import sample. No private corpus image is included.
- `screenshots/`: actual iPhone and 13-inch iPad simulator captures, with provenance and hashes in the screenshot manifest. Android screenshots must not be used in the iOS listing.
- `app-icon-1024.png`: the existing bundled App Store icon; Xcode supplies it with the build.
- `../ios-user-guide.md`: iPhone/iPad support instructions.
- `website-update.md`: published website changes and release-state guidance.
- `device-qualification.md`: exact signed-build and real-camera checks that remain.
- `../privacy-policy.md`: revised cross-platform policy source. Published at https://freevia.org/sudoku-buddy/privacy/; see `website-publication.json`.

## Local release checks

Run resource/replay guards with `python3 -m unittest discover -s tools/ios -p 'test_*.py'` and storage tests with `swift test --package-path iosApp/Persistence`.

Build an unsigned device archive (this verifies device compilation, not distribution):

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
  -project iosApp/SudokuBuddy.xcodeproj -scheme SudokuBuddy -configuration Release \
  -destination 'generic/platform=iOS' -archivePath .tools/SudokuBuddy-Submission.xcarchive \
  CODE_SIGNING_ALLOWED=NO archive
python3 tools/ios/audit-release.py \
  .tools/SudokuBuddy-Submission.xcarchive/Products/Applications/SudokuBuddy.app
```

Keep build numbers unique for actual uploads. Account holder registers the bundle ID, selects the intended team, creates the app record and supplies legal/contact/territory details. Then produce and validate a signed archive, upload to TestFlight, qualify on real hardware, complete the current App Store Connect questionnaires, attach the screenshots/review sample, select that exact build and submit. Use manual release after approval.

Current Apple upload minimum is Xcode 26 with the iOS 26 SDK or later. Recheck before upload: [SDK requirements](https://developer.apple.com/news/upcoming-requirements/?id=04282026a). Our installed toolchain is Xcode 27.1; Apple acceptance still requires validation of the final signed build. Screenshot requirements: [Apple specifications](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications).

This packet does not claim real-camera, VoiceOver, live production-report delivery or Apple distribution qualification. Simulator proof and corpus equality do not establish those results.
