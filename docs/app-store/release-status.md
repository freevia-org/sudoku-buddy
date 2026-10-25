# iOS release status — 25 October 2026

App Store Connect record: **Sudoku Buddy by Freevia**, Apple ID `6825975489`,
bundle `org.freevia.sudokubuddy.ios`, Freevia EOOD team `8C782Q826S`.

Version **1.0.2 (1)** was exported and uploaded on 23 October, processed, selected
and submitted to App Review. App Store Connect was checked live on 25 October at
11:11 UTC and showed **Waiting for Review**. Automatic release after approval is
enabled and was verified after reloading the release settings. No Apple messages or
action requests were shown. Submission ID: `14940e4d-2614-4369-b016-e23bb890d0b0`.

The earlier preparation packet predates account setup. Local Apple Distribution
and Apple Development identities are now available, and App Store Connect is
accessible through the existing Freevia account. Do not create another app record
or reuse build 1 for a new upload.

## Candidate and verification

The submitted 1.0.2 IPA is in the MacMini release workspace at
`apple-release/Sudoku-export/SudokuBuddy.ipa`. SHA-256:
`43bd2836588bd9d448b2cb4c9936be306fd8ffad643f5ade5f3465cb08eb7dea`.
Its hash matches the recorded export. On 25 October, the unpacked export passed
deep/strict code-signature verification and `tools/ios/audit-release.py`, including
the production report endpoint, 71 language catalogs, model, privacy manifest and
absence of Debug fixtures. Recorded provisioning is App Store distribution for
the correct bundle/team, with debugging disabled and no device list.

The upload succeeded with one non-blocking warning: the pinned OpenCV framework
has no matching dSYM. Preserve this limitation for crash-symbolication work.

Current `main` is `33d0de3`. The prepared 1.0.3 candidate was built from `8573614`;
all changes merged since that candidate commit are documentation-only. Compared with
the submitted 1.0.2 candidate baseline `16c3d46`, intervening iOS changes affect
signing/export configuration, CI, UI tests, and documentation; application sources,
shared functionality, and resources are unchanged. Android 1.0.3 changes (R8 and
Android UI dependencies) do not apply to iOS.

### Prepared 1.0.3 candidate (not uploaded)

An iOS **1.0.3 (build 2)** candidate was prepared locally from `8573614` without
changing tracked source or touching App Store Connect. The archive audit confirms
version/build `1.0.3 (2)` and the expected bundle ID. Its locally exported IPA passed
deep/strict signature verification with the Freevia EOOD App Store profile, debugging
disabled, and the correct team. Archive and export audits passed. The IPA SHA-256 is
`d3bd03498b5049f0ed350326418eb1d2dde9272b0fad9e17462c867418010df3`.

Candidate checks passed: five simulator UI workflows, 36 storage tests, 138 shared
JVM/native tests, 14 resource checks, and geometry checks (one optional corpus test
skipped). No replacement build was uploaded; version 1.0.2 remains in review. Apple
requires the current version to be **Ready for Distribution** before creating the next
App Store version record. After that, create version 1.0.3 and upload this prepared IPA
if its source and release notes are still current.

## Signing and testing notes

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
invitation was sent by this check. The 1.0.3 (2) candidate above is a local App Store
export; it has not been uploaded to App Store Connect or TestFlight.
