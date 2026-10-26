# iOS release status — 26 October 2026

App Store Connect record: **Sudoku Buddy by Freevia**, Apple ID `6825975489`,
bundle `org.freevia.sudokubuddy.ios`, Freevia EOOD team `8C782Q826S`.

Version **1.0.2 (1)** was exported and uploaded on 23 October, processed, selected
and submitted to App Review. App Store Connect was checked live on 26 October by the
SUB iOS session and still showed **Waiting for Review**. Automatic release after
approval remains enabled. No replacement version was uploaded or submitted.
Submission ID: `14940e4d-2614-4369-b016-e23bb890d0b0`.

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

Current `main` is `f0b40c5`. The latest iOS candidate below was built from
`5c615b69c6b11a2eaae86720dddf42df2514e8be`; subsequent changes through main affect
Android Play versioning, candidate validation, and documentation, not iOS app sources.

### Prepared iOS 1.0.3 candidate from merged source (not uploaded)

An iOS **1.0.3 (build 2)** candidate was rebuilt from merged source commit
`5c615b69c6b11a2eaae86720dddf42df2514e8be`. The signed IPA is preserved in the
SUB iOS workspace at `.tools/ios-1.0.3-2-5c615b6/export/SudokuBuddy.ipa`.
SHA-256: `a0c64500115d4b0f4eed672e0e32d6394be22cb86329c2bd0ae3ec343d018588`.
The export passed deep/strict signature checks with the Freevia EOOD App Store
profile, debugging disabled, and the correct team. Archive and export resource audits
passed for the production endpoint and all 71 bundled catalogs.

Six simulator UI workflows, 36 storage tests, 138 shared JVM/native tests, 14 resource
checks, and four geometry checks passed; one optional corpus test was skipped. This
candidate remains local and has not been uploaded. Physical iOS qualification and Apple
upload processing are outstanding. The iOS About view currently displays the version
but not the build number. Apple requires version 1.0.2 to be **Ready for Distribution**
before creating version 1.0.3; automatic release of 1.0.2 remains enabled.

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
