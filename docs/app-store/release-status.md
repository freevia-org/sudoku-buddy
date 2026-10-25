# iOS release status — 25 October 2026

App Store Connect record: **Sudoku Buddy by Freevia**, Apple ID `6825975489`,
bundle `org.freevia.sudokubuddy.ios`, Freevia EOOD team `8C782Q826S`.

Version **1.0.2 (1)** was exported and uploaded on 23 October, processed, selected
and submitted to App Review. The app list was checked live on 25 October and
showed **Waiting for Review**. Manual release was selected at submission.
Submission ID: `14940e4d-2614-4369-b016-e23bb890d0b0`.

The earlier preparation packet predates account setup. Local Apple Distribution
and Apple Development identities are now available, and App Store Connect is
accessible through the existing Freevia account. Do not create another app record
or reuse build 1 for a new upload.

## Candidate and verification

The preserved IPA is in the MacMini release workspace at
`apple-release/Sudoku-export/SudokuBuddy.ipa`. SHA-256:
`43bd2836588bd9d448b2cb4c9936be306fd8ffad643f5ade5f3465cb08eb7dea`.
Its hash matches the recorded export. On 25 October, the unpacked export passed
deep/strict code-signature verification and `tools/ios/audit-release.py`, including
the production report endpoint, 71 language catalogs, model, privacy manifest and
absence of Debug fixtures. Recorded provisioning is App Store distribution for
the correct bundle/team, with debugging disabled and no device list.

The upload succeeded with one non-blocking warning: the pinned OpenCV framework
has no matching dSYM. Preserve this limitation for crash-symbolication work.

The checkout was advanced from `16c3d46` to `3673e3a`. Those intervening changes
affect CI, README and UI tests; application sources and resources are unchanged.
Hosted iOS and Android CI passed for `3673e3a`. No replacement build was uploaded
and the submitted candidate was left in review.

## Future signed builds

The Xcode app and UI-test targets now select Freevia EOOD for automatic signing.
`iosApp/ExportOptions-AppStore.plist` exports locally using that team, preserves
the supplied version/build and does not upload. Use a unique build number for a
new upload. Follow the archive command in this packet with
`DEVELOPMENT_TEAM=8C782Q826S` and signing enabled; export using:

```sh
xcodebuild -exportArchive -archivePath PATH_TO_NEW_ARCHIVE \
  -exportOptionsPlist iosApp/ExportOptions-AppStore.plist \
  -exportPath PATH_TO_NEW_EXPORT
```

No agreements or account permissions were changed. Hardware qualification in
`device-qualification.md` remains unrecorded. TestFlight tester distribution must
be checked separately from App Store review status. The live TestFlight page on
25 October lists build 1 as **Ready to Submit** (88 days remaining), with no
testing groups, invites or installs shown. Creating a tester group and selecting
testers remains necessary before collecting TestFlight hardware results; no
invitation was sent by this check. Future archive builds also
need additional disk space: this Mac had less than 1 GiB free at this check.
