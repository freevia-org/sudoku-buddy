# iOS release status — 1 November 2026

App Store Connect record: **Sudoku Buddy by Freevia**, Apple ID `6825975489`,
bundle `org.freevia.sudokubuddy.ios`, Freevia EOOD team `8C782Q826S`.

Version **1.0.2 (1)** was exported and uploaded on 23 October, processed, selected
and submitted to App Review. Publishing checked App Store Connect on 1 November: the
submission is currently **In Review**, still using build 1. The review history includes an
initial rejection under **4.3.0 Design: Spam**, with the notice citing Guidelines 4.3(a)/(b)
and 4.2.6. Apple's 26 October message at 6:33 PM was followed by Antoni's two replies at
10:29 PM and 11:53 PM. The latest message, verified on 30 October for submission
`14940e4d-2614-4369-b016-e23bb890d0b0`, says Apple will continue its review. This is a
progress acknowledgment, not an approval. The nine-point factual response was sent through
the Resolution Center. Publishing also confirmed that build **1.0.5 (3)** is not present in
TestFlight/Build Uploads and has not been uploaded or submitted.
The app is not available on the App Store.

The prepared 1.0.3 (build 2) candidate remains local and unuploaded. Do not upload or
resubmit it as-is: its only changes from the submitted app are About version/build display
and translations, which do not address the review concerns. Apple's notice says not to
resubmit until significant changes if the cited duplication/template concerns apply; the
nine requested factual answers have already been sent. Wait for Apple's response or make
substantial product changes before preparing and submitting a new candidate.

The earlier preparation packet predates account setup. Local Apple Distribution
and Apple Development identities are now available, and App Store Connect is
accessible through the existing Freevia account. Do not create another app record
or reuse build 1 for a new upload.

## Merged shared-image candidate — 1.0.5 (3)

Merged in PR #96 at `d72c477`, iOS 1.0.5 (3) adds an embedded Share extension and
an App Group inbox for images received from other apps. The containing app offers
Read/Keep/Delete this photo and reuses existing recognition, correction, feedback-consent
and history persistence. It is an unsigned Release candidate, not an uploaded build.
Its marketing version aligns with Android 1.0.5; build 3 supersedes
the preserved, unuploaded iOS 1.0.3 (2) candidate.

The app and extension build locally for simulator and device. Forty-two persistence
tests (including four inbox and two language-choice tests), three host-app shared-image UI workflows, and
sixteen resource/localization checks pass. The Release bundle audit verifies the embedded
extension, matching versions, image-only activation, privacy manifests and absence
of Debug fixture switches. Current listing materials and fourteen screenshots
pass validation. The host UI tests stage the inbox through a Debug-only fixture;
they do not qualify the system share sheet or signed App Group exchange.

On 1 November, Publishing registered App Group `group.org.freevia.sudokubuddy.ios`, enabled
it on the app and share extension, and generated App Store profiles for both identifiers.
The profiles have not yet reached the Mac iOS release workspace, so a signed archive and
IPA are not ready. Signed iPhone/iPad share-sheet testing is still required. See
[iOS shared images](ios-shared-images.md) for behavior, limits and device checks.
Apple's existing rejection remains unresolved; this change does not guarantee
approval. No binary was uploaded or submitted for review.

### Current-source rebuild (1 November 2026)

The unsigned Release device build was rebuilt from current `main` commit
`5bb97a7dee3b97a2935a5e12b8ae76a83f8db6b9`, including PR #101's About screen build-number
display. The 1.0.5 (3) device build succeeded; the 16 resource/localization tests, App Store
materials check (14 screenshots), and release bundle audit passed. The audit confirmed the
embedded share extension, matching app/extension versions, all 71 locale catalogs, expected
privacy manifest and production endpoint, and no Debug fixtures. The unsigned app binary's
SHA-256 is `82156de5dbe1ba4a8f97966e567e7703ba5552e5f2cf4ed38652baf7392b24a2`.

This is an unsigned local candidate, not an IPA for distribution. On 1 November, Publishing
registered `group.org.freevia.sudokubuddy.ios`, enabled it for the main app and share extension,
and generated App Store profiles for both identifiers with Freevia EOOD's distribution
certificate. The iOS release session still needs to obtain those profiles, sign and audit a new
archive, and confirm that a distributable IPA is ready. No signed archive or IPA was produced by
the current-source rebuild; no upload or review submission occurred.

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

The 29 October source audit of signed candidate `5c615b69c6b11a2eaae86720dddf42df2514e8be`
predates PR #96 and must not be used to claim that the signed IPA matches current iOS
sources. PR #96 adds the share extension and App Group inbox. The previous 1.0.3 (2)
candidate below remains historical and does not contain this feature. The current 1.0.5
(3) candidate has passed local build/resource checks, but still needs App Group signing,
physical share-sheet qualification, and Apple review strategy; the existing rejection
remains unresolved.

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
but not the build number. The rejection concerns product differentiation and app
provenance, which this candidate does not change. Do not upload it as-is; respond to
Apple through Resolution Center or make substantial product changes first.

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
`device-qualification.md` remains unrecorded. Publishing's 26 October App Store
Connect audit found no TestFlight groups for Sudoku Buddy; the visible page offered
**New Group**, with no group or invite entries shown. No TestFlight beta cohort or
feedback is recorded. The prepared 1.0.3 (2) candidate above is a local App Store
export; it has not been uploaded to App Store Connect or TestFlight.
