# Sudoku Buddy: final build and Google Play handoff

Updated 10 October 2026. Status: **release-preparation branch integrates the camera,
recognition, tutor and opt-in private report work. No signed candidate for these changes has
been built, uploaded, or published.** The existing upload certificate remains in use; no new
certificate is needed.

The earlier signed 1.0.0 (120) candidate is sealed and predates report submission. Do not use
it for the current release. The private intake Worker is live at
`https://sudoku-buddy-feedback.antoni-ivanov.workers.dev/v1/reports`. A synthetic ZIP upload
returned 201, an identical retry returned 200 with the same receipt, and the test object was
deleted. The app now has manual submission, a default-off automatic-sharing setting, durable
reading/correction history, and receipt-based deletion requests. App unit tests and release
Kotlin compilation pass, but no device-level upload has been verified. The privacy-policy and
Play declaration drafts are local only; the public policy and Play Console answers still need
updating after device verification.

The manual Play candidate workflow takes an immutable commit SHA and explicit version code
121. It requires the existing signing secrets and verifies bundle identity, certificate and
generated APK alignment. It cannot run until this feature branch is merged because GitHub
requires a manual workflow to exist on the default branch. Firebase distribution is now
disabled for pushes and ordinary manual CI; an explicit main-branch opt-in is required.

Remaining before Play submission: run the candidate on a physical device and verify real
report upload, receipts, correction updates and deletion requests; run the remaining camera,
TalkBack and 16 KB runtime qualification; publish the matching privacy policy; update the
listing and Data safety form; and complete Play's internal test and delivery checks. The
Console upload/review action remains separate from this preparation.

Report-copy retention is not yet operationally verified. The Worker assigns each KV revision
a 90-day expiry, and the private review tooling now records that deadline and can remove all
copies under one receipt folder. Before accepting real reports, Freevia must designate the
private, non-synced review workspace, install and verify its daily expiry task, and inventory
and remove legacy exports or report-derived corpus items. Until those controls are evidenced,
the 90-day limit is a required policy rather than a verified operational guarantee; do not
enable report intake for general release.

The current status and remaining gates below supersede the historical review snapshots
later in this document. The older 343-test run and debug-signed version-code-1 artifact
are historical evidence only. This document does not approve distribution.

## Historical candidate-120 evidence (not the current release candidate)

The verified candidate-120 snapshot includes the reviewed app/core changes, the completed
camera/tutor work, the GitHub feedback draft action, and a correction-sheet fix found during physical
release testing. The editor now opens expanded and scrolls; its final actions were
verified at 1080 x 2160 and at 200% text size. The earlier candidate-120 binary was
replaced locally before any AAB upload, so only the hash below identifies this candidate.

| Area | Verified result and current limit |
| --- | --- |
| Signed build | Final coordinated verification: `BUILD SUCCESSFUL in 2m 29s`, after the fresh-process rebuild. Version 1.0.0 (120), package `org.freevia.sudokubuddy`, AAB size 71,282,802 bytes. SHA-256: `57AFB7C83E84C182826EBEA270F3CF5E8A9DD5C9BE547558C0AC9DB78719F9C9`. |
| JVM tests | 368 reported tests across 85 suites: model 23, solver 109, vision 75, recognition 54 and app debug unit tests 107; zero failures, errors or reported skips. Some opt-in benchmark, export and replay methods return early unless enabled; a reported pass does not prove those workloads ran. |
| Python checks | 20 release-tool tests and 3 recognition data-guard tests passed. Their unchanged results are preserved in the final summary. |
| Lint | Zero errors or fatal issues, 22 warnings and 4 hints in the debug lint report. `lintVitalRelease` was skipped; full app debug lint completed. A reused-process Kotlin lint crash was resolved by running the checks with `--no-daemon --no-build-cache`; no lint rule was disabled. |
| Stable signing | The final AAB signature is valid and matches `CN=AI Sudoku, O=AI Sudoku, C=BG`, SHA-256 `a5:10:d8:2b:87:e7:06:9b:53:d5:20:be:ca:91:5b:35:2a:e9:6c:ea:85:0c:68:ca:00:61:17:e3:5d:75:d3:e5`. The earlier app name in the certificate is expected. |
| Bundle and generated APKs | Bundletool 1.18.3 validation passed; config requests `PAGE_ALIGNMENT_16K`. Four ABIs, 20 native libraries and ten checked 64-bit libraries. All four final phone APKs passed signature verification and `zipalign -c -P 16 4`. The AAB declares minSdk 26 and targetSdk 36; generated device-specific APK variants can raise their own minimum SDK. |
| Release phone upgrade | Installed release upgraded from 117 to signed 120 without uninstalling. Previously visible history entries remained present. Final rebuilt APKs were then installed over 120, preserving the new capture and its reading warning. This is a local same-key upgrade, not Play delivery. |
| Fresh camera capture | A photographed pencil-note cluster at r6c4 was read as 8 and explicitly flagged for review. Reopening retained that warning. Correcting it to Empty cleared the warning; Check then confirmed the handwritten answers, and the full four-stage hint plus ten-step reasoning replay worked. This one-page case is not independent OCR accuracy evidence. |
| Editor and screenshots | Empty, Printed and That is right actions are visible/reachable after the fix, including at 200% text. Five current 1080 x 2160 store screenshots were captured from the final release and losslessly encoded as 24-bit RGB PNGs; rendered pixels match the originals. Phone display size and font scale were restored. See `docs/store/screenshots/README.md`. |
| Source provenance | Dirty tree, 218 scoped paths, matching pre-build/current fingerprint `e613b5564422db0b967edd1d1e6106bd9c1e25fe0c97e6ffc279aab022361e78`. Exact scoped sources, corpus labels and build release notes are preserved in `build/publishing-preparation/source-1.0.0-120.zip`; archive SHA-256 `f696691e429c3f15ed4c43b3df2c66afc26a54527d70f4bbe532739b52b39fd8`. No signing files or private corpus photographs are included. No commit or push was performed. |
| Console | Rechecked on 8 October after browser access recovered: Freevia is an Organization account; Latest releases shows no releases, latest bundles is None and All app bundles is empty. Code 120 has no visible uploaded-code conflict. Internal testing is inactive, with 0 of 3 tasks complete. Listing/content changes remain unsubmitted and managed publishing is off. |
| Play signing migration | Completed with explicit user authorization on 8 October at approximately 19:43 UTC (22:43 Kyiv): the existing release key was imported using the Console's Java-keystore/PEPK encrypted-key flow. The downloaded App signing key certificate and registered Upload key certificate both match the stable SHA-256 above. Digital Asset Links agrees and the Previous keys section is no longer shown. Play-delivered installation and upgrade remain untested. |

Current handoff: `build/publishing-preparation/sudoku-buddy-1.0.0-120-handoff3/`. It contains the signed
AAB, explicit store assets and release materials, evidence, `release-record.json` and a
complete `SHA256SUMS.json` inventory. Verify it with:

```powershell
python tools/release/package_candidate.py verify build/publishing-preparation/sudoku-buddy-1.0.0-120-handoff3
```

The original `sudoku-buddy-1.0.0-120` and `sudoku-buddy-1.0.0-120-handoff2` handoffs are
preserved. Handoff 3 records the completed signing migration and retains the sanitized
evidence from handoff 2. The AAB and the 218 scoped source files are unchanged; original
raw evidence is retained locally. The matching ZIP is beside the handoff directory.

Java 21 and 25 `jarsigner` report `jar verified.` with no unsigned-entry warning. They
also warn that streaming `JarInputStream` does not recognize these signatures because
AGP places signing metadata after the payload. Random-access `JarFile` verification
succeeds; the raw warnings are retained in evidence. No repacking was used to hide them.
See [the streaming verifier's ordering requirements](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/jar/JarInputStream.html).

Production qualification remains: a verified 16 KB Android runtime; broader physical
camera/recognition and spoken TalkBack testing; final privacy/support-retention and
Data safety reconciliation; and Play-delivered certificate, installation and upgrade checks.
The current host has no emulator/system image, so static alignment
success is not represented as a runtime pass. The local privacy/store-copy updates
have not been published to the website or Console.

## Repository move and candidate preparation

The user authorized transfer from `tony-xmelon/sudoku-buddy` to
`freevia-org/sudoku-buddy` on 8 October 2026. `freevia-org` is a regular GitHub user
account. The recipient accepted and ownership was verified on 8 October 2026 at
https://github.com/freevia-org/sudoku-buddy. The local origin now points at that URL;
the shared camera and tutor worktrees inherit it. The known Freevia publication checkout
was also checked for the old remote and updated where needed.

All six repository secret names were verified after transfer:
ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD,
ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD, FIREBASE_APP_ID and FIREBASE_TOKEN. Values
were not exported. Actions remains enabled with all actions allowed. `freevia-org` has
admin access and `tony-xmelon` retains push access as a collaborator. Git access to the
new origin was verified without fetching, pulling, pushing or changing development files.

The new `.github/workflows/play-candidate.yml` is prepared locally. It runs only by manual
dispatch, requires a full reviewed commit SHA and explicit version code, fails if signing
credentials are missing, builds/tests, checks all bundle ABIs and manifest, validates with
official bundletool 1.18.3, verifies the signature and documented upload certificate, and
archives a candidate with evidence and a SHA-256 digest. It has no Firebase or Play upload
step. The workflow itself has not been pushed or dispatched. The local signed-candidate
build and release-tool checks are recorded above; they do not establish a successful CI run.

`tools/release/check_play_bundle.py` can also audit a local candidate using bundletool's
manifest/config output. Its synthetic-artifact tests check release-blocking metadata,
native-library and ABI failures. Static checks do not replace physical-device testing.

The candidate is being prepared from the current local tree, including the accumulated
camera, recognition and tutor work. Preserve the exact scoped sources and their hashes;
a base commit SHA alone cannot reproduce this dirty-tree build. Other worktrees and
shared-device operations must remain coordinated with their owners.

A fresh Console read on 8 October reconfirmed the package identity and saved listing/content
changes under **Changes not yet submitted for review**. Send app for review is disabled.
The internal-testing dashboard shows 0 of 3 tasks completed (testers, release, confirmation)
and no releases. Managed publishing is off. The subsequently authorized signing change
below did not upload an AAB/APK, add testers, start a rollout or accept new terms.

On 8 October, the user explicitly authorized configuring Play with the existing release
key and sending its encrypted copy to Google to preserve existing Firebase installs and
history. The Console's existing Java-keystore import was saved using a PEPK encrypted ZIP.
The downloaded App signing key certificate has subject `CN=AI Sudoku, O=AI Sudoku, C=BG`,
a 4096-bit RSA public key, and SHA-256
`A5:10:D8:2B:87:E7:06:9B:53:D5:20:BE:CA:91:5B:35:2A:E9:6C:EA:85:0C:68:CA:00:61:17:E3:5D:75:D3:E5`.
The registered Upload key certificate and Digital Asset Links show the same SHA-256.
The previously observed different certificate and Previous keys section are no longer
shown. App signing is marked **In use**, with no pending-processing message. Handoff 3
includes the public `evidence/play-app-signing-certificate.der`,
`evidence/play-signing-record.json` and `evidence/play-signing-confirmed.jpg`.
The signing configuration now matches the Firebase/local identity; this does not
yet verify a Play-delivered upgrade or preservation of app data through that upgrade.
Google documents the custom-key option before an open-testing or production rollout in
[Play App Signing setup](https://support.google.com/googleplay/android-developer/answer/9842756).

No existing Play releases or uploaded bundles needed migration. Keep the keystore,
password and encrypted export outside the release handoff and source repository.
Do not uninstall existing Firebase copies or clear their data to test migration: the app
has no full history export/import. The current phone already runs code 120. A later
Play-delivered upgrade test needs an agreed same-key device with a lower installed code,
or a future higher-code Play build, and must verify the delivered certificate and retained
history, photographs, corrections and settings.

## Authorization boundary

Prepare and verify release materials locally. Do not upload an AAB or APK, add or select
testers, submit for review, start a test rollout, or publish until development is finished
and the user explicitly authorizes the relevant action. Record the exact build and track
in that authorization. Internal testing is also distribution and is behind this gate.

Do not push or dispatch the current CI workflow merely to prepare a package: its
`distribute` job can upload an APK to Firebase on main-branch pushes and manual runs.
`appDistributionUploadRelease` and `distributeLocal` also upload and are outside this task.

## Ready items and evidence

| Item | State and evidence |
| --- | --- |
| Play app | Created with `org.freevia.sudokubuddy`, per task handoff |
| Main listing and privacy URL | Completed, per task handoff |
| Data safety and other declarations | Completed, per task handoff; reconcile with the final app before release |
| IARC rating | Completed, per task handoff; retain the issued ratings and certificate rather than predicting them |
| Source identity | `app/build.gradle.kts`: application ID and namespace `org.freevia.sudokubuddy`; debug suffix `.debug` |
| Android levels | minSdk 26, targetSdk/compileSdk 36 |
| Version | Final candidate verified as versionName `1.0.0`, versionCode `120`. The Gradle local default remains `1` when `BUILD_NUMBER` is absent. |
| Bundle packaging | `:app:bundleRelease`; CI artifact `sudoku-buddy-bundle`; expected file `app/build/outputs/bundle/release/app-release.aab` |
| Native dependency | Android OpenCV 4.12.0; APK ELF load alignment checked by `:app:checkNativeAlignment` |
| Permissions and manifest | Final signed AAB passed the audit for identity, SDK, backup declaration, photo-sharing provider and expected permissions. |
| Runtime services | Firebase is a build plugin, not a listed app runtime dependency |
| Artwork | `docs/store/icon-512.png`, `docs/store/feature-graphic-1024x500.png` exist |
| Release copy | `docs/play-release-notes-en-US.txt` prepared for first release |
| Signing reference | Final candidate certificate and signature verified against `docs/signing.md`. |

The task handoff supplied the initial completion status; the fresh Console inspection
reconfirmed saved changes pending review, not an approved or published release.
No AGENTS.md was found in the repository or its filesystem ancestors during this audit.

## Remaining gates

| Gate | Current finding | Required evidence |
| --- | --- | --- |
| Runtime and accessibility | Upgrade 117 to 120 preserved visible history. The fresh-camera review/correction path, hints and replay passed. The final editor was verified at normal and 200% text. | Complete outstanding rows in `play-internal-test-results.csv`, including spoken TalkBack, varied/repeated captures and final signed process/rotation cases. |
| 16 KB runtime | Static ELF checks, `PAGE_ALIGNMENT_16K` config and generated-APK ZIP alignment passed. No 16 KB emulator/system image is available locally. The physical phone uses 4 KB pages. | Run the final candidate in an environment reporting `adb shell getconf PAGE_SIZE` = 16384 and exercise OpenCV capture without relying on compatibility mode. Static alignment alone does not complete this gate. |
| Live listing assets | Five final release-120 screenshots and their provenance are prepared locally. Website imagery has historical build-116 provenance. | Use the reviewed local assets when updating the listing; live listing and website changes require their own publication step. |
| Privacy and declarations | Candidate 120 describes offline processing and user-directed share-sheet actions. Draft local policy/store copy now describes the deployed private report intake, but it is explicitly unpublished and conditional on app integration. | Verify the shipped consent/settings behavior and exact ZIP payload; test app submission/receipt and deletion against the Worker; confirm 90-day expiry; publish the matching policy; update Play listing copy; and reassess the actual Data safety form. At minimum assess Photos for the uploaded puzzle image, classify OCR/corrections/diagnostics from the ZIP, choose actual purposes and optionality, confirm Cloudflare's service-provider role, HTTPS, and user deletion requests. The previous “No collection/sharing” answer is stale. |
| Play version, signing and eligibility | Final code 120 and the stable local certificate are verified. Console shows an Organization account, no uploaded bundles or releases, and an inactive internal track. After the authorized key import, both Play app-signing and upload-key certificates match the Firebase/local certificate. | Verify the certificate of an actual Play-delivered install and qualify the same-key upgrade with a lower-version baseline or future higher Play build. Confirm retained app data; Console configuration alone does not complete this gate. Distribution requires separate authorization. |
| Distribution authorization | Encrypted-key import was explicitly authorized and completed. No AAB/APK upload, review submission, tester changes or rollout performed. | Explicit user authorization identifying the exact AAB hash, track and allowed tester/distribution actions. |

The working-tree recognition baseline is development evidence, not release acceptance:
`docs/recognition/baseline.json` records 108/110 detections and explicitly notes classifier
training overlap with the corpus. Do not present it as independent accuracy validation.

## Repeatable local qualification procedure

Use the final checkout with configured stable signing credentials. Do not put keystore
passwords or signing files in the repository or release package. Set BUILD_NUMBER to the
chosen code; all commands below are build/verification commands and do not upload.

```powershell
# Set BUILD_NUMBER to the approved version code before running these commands.
.\gradlew.bat :app:checkReleaseSigning --console=plain
.\gradlew.bat build --console=plain
.\gradlew.bat :app:checkNativeAlignment :app:bundleRelease --console=plain
```

Stop on any nonzero exit code. Inspect JUnit and lint reports; a green CI run can skip
corpus tests because the photographs are not committed. Run relevant corpus tests locally
against the final sources and archive their reports. Do not regenerate a baseline merely
to accept a regression. The developer should record accepted failures, if any, explicitly.

With a separately obtained, pinned official bundletool JAR and SDK tools, validate the
candidate. Replace placeholders with real tool paths and candidate paths:

```powershell
java -jar <bundletool.jar> validate --bundle=<app-release.aab>
java -jar <bundletool.jar> dump manifest --bundle=<app-release.aab> --module=base
java -jar <bundletool.jar> dump config --bundle=<app-release.aab>
keytool -printcert -jarfile <app-release.aab>
Get-FileHash -LiteralPath <app-release.aab> -Algorithm SHA256
```

Check application ID, version name/code, minimum/target SDK, absence of debuggable=true,
permissions, provider authorities and backup exclusions in the final manifest. The upload
certificate SHA-256 should match the selected upload key; the current documented key is
`a5:10:d8:2b:87:e7:06:9b:53:d5:20:be:ca:91:5b:35:2a:e9:6c:ea:85:0c:68:ca:00:61:17:e3:5d:75:d3:e5`.
Verify the JAR signature too (for example `jarsigner -verify -verbose -certs`); keytool
printing a certificate alone is not signature verification. Investigate warnings according
to Android upload-key requirements; self-signed Android certificates are expected.

Bundle config should request `PAGE_ALIGNMENT_16K`. Inspect ELF load alignment for every
arm64-v8a/x86_64 library in the bundle and check RELRO compatibility where applicable.
Build APKs locally with bundletool for test devices, then run SDK `zipalign -v -c -P 16 4`
and `apksigner verify --print-certs` on the generated APKs. Record the key used for those
local APKs: it does not prove what certificate Play will use. In a 16 KB test environment,
`adb shell getconf PAGE_SIZE` should report 16384; exercise OpenCV scanning without relying
on compatibility mode. Do not install over anyone's existing data without an agreed test plan.

## Local handoff package

Assemble only after the final candidate has passed qualification. Keep private source
photographs, tester addresses, keys, passwords, service-account files and tokens out.

```text
sudoku-buddy-1.0.0-<versionCode>-<commit>/
  app-release.aab
  SHA256SUMS.json
  release-record.json
  play-release-notes-en-US.txt
  play-console-submission.md
  play-release-readiness.md
  play-internal-test-results.csv
  store/icon-512.png
  store/feature-graphic-1024x500.png
  store/screenshots/README.md
  store/screenshots/01-scan.png ... 05-history.png (final release provenance required)
  evidence/ (build, lint, signature, native-alignment and test reports)
```

`tools/release/package_candidate.py prepare` creates `release-record.json` and
`SHA256SUMS.json`; its `verify` command checks the packaged file inventory and hashes.
The record identifies the candidate as **NOT APPROVED FOR DISTRIBUTION**. Preserve the
exact scoped source separately in a local archive with a recorded hash; omit credentials
and private corpus/feedback material. Supplement the generated record with the final
build command, AAB size, upload certificate, intended track, tool versions, device
builds/page sizes, reported skips and opt-in test limits, unresolved issues, screenshot
provenance, declaration reconciliation and any subsequent explicit authorization.
A prepared package and matching hashes do not complete the outstanding runtime/Play gates.

Firebase composed notes now use the app's actual version name and code and are regenerated
for each distribution. Use the separate Play notes file for the Play handoff.

## Historical code review safeguards, 8 October 2026

This section records the earlier review stage. The current signed-candidate results and
remaining gates at the top of this document take precedence.

Local Firebase distribution now requires stable signing and native-alignment checks,
matching the plugin upload path. Signing validation checks all required environment
variables, and invalid Play version codes fail at build configuration. No distribution
was performed while reviewing these changes.

Backup exclusions now cover every storage domain, including refused photos in external
app storage, with explicit paths. Android documents that some manufacturers still permit
device transfers when `allowBackup` is false; the extraction rules close that gap.
See [Android backup configuration](https://developer.android.com/identity/data/autobackup).

The offline bundle audit additionally verifies the photo-sharing provider's authority,
private visibility and URI grants. Ten release-safeguard tests passed locally; the
general CI workflow now runs them as well. These checks do not certify Android runtime
behavior or replace the remaining candidate qualification gates above.

The RELRO audit now checks whether protection rounded to 16 KB pages would overlap
writable data. An unaligned RELRO end in an isolated load segment can safely cover only
padding; the former unconditional end-alignment check incorrectly rejected that layout.
This follows the protection behavior in the
[Android linker](https://android.googlesource.com/platform/bionic/+/android16-qpr2-release/linker/linker_phdr.cpp#1391).
All ten 64-bit libraries in the existing debug merged-library output passed the corrected
audit. Recheck the final release bundle and run the 16 KB runtime test before distribution.

## Historical app and core review, 8 October 2026

The following findings and test counts describe the earlier debug-signed review artifact.
They are retained for traceability and do not describe the current code-120 candidate.

This review started with substantial uncommitted camera, recognition, vision and tutor
work already present. The review preserves that work and adds targeted corrections on
top of it. The whole working-tree diff is not attributable to this review, and a passing
test against this checkout does not identify a reproducible release until the final
source revision is recorded. No upload, rollout or website publication was performed
as part of the code review.

The most serious solver finding was an incomplete displayed forcing-chain proof:
pruning retained the last causal placement but could omit earlier placements required
to justify the deduction. In the review sample, 37 of 152 derived links lacked displayed
prerequisites. The fix retains the full prerequisite closure and adds a regression that
replays and checks the displayed deductions. Related fixes cover missed naked subsets,
grid immutability, invalid candidate resurrection and false solved states. Solver
search help without a displayable argument remains explicitly labeled as such.

History writes now use a flushed temporary file and atomic replacement where supported;
a failure while writing that temporary file leaves the previous record intact. Saved
records include reading geometry,
uncertainty, user-entered cells and recognition reports as well as the grid; older
records remain readable. Asynchronous writes are ordered before entering the IO
dispatcher, and selection generations prevent stale load/save completions from
replacing the current puzzle. Save errors are visible instead of silently losing edits.
The selected history entry restores after activity/process recreation when its record
was saved. On the connected Android 16 phone, an edited puzzle survived reopening and a
controlled background process kill followed by a cold launch with a new process ID.
Abrupt termination during a pending write remains outside that verified case.

Puzzle preparation, history decoding/summaries and practice checking have moved off the
main thread. Bounded shared analysis caches avoid repeating expensive solver work for
selection and tutor navigation. Native image buffers are released on reviewed success
and failure paths, reducing retained OpenCV allocations. The first hard walkthrough
still took about 1.5 seconds in a desktop measurement; that is a reason to keep
background preparation and loading feedback, not evidence of phone responsiveness.

Camera startup now reports failures with a retry action, avoids blocking disposal on a
provider future, and guards callbacks after the camera screen is disposed. Permission
denial retains access to saved puzzles and offers app settings; permission state is
refreshed after returning. Grid cells expose individual semantic descriptions and
actions for correction/practice. Tutor controls accommodate short windows, and refused
photo timestamps no longer share a mutable formatter between worker threads. The phone
smoke test verified editing, saved history, checking, hints, tutor navigation and
landscape layout, with all 81 grid cells exposing accessibility descriptions. Spoken
TalkBack behavior, live camera focus/capture and permission recovery still need the
remaining physical test matrix.

### Historical local verification: debug-signed version code 1

| Area | Evidence from the earlier coordinated debug-signed run |
| --- | --- |
| Model and solver | 23 model and 109 solver tests passed, including the new proof checks. |
| App | 99 unit tests passed, including history metadata, atomic writes, bounded analysis caching and concurrent diagnostic names. |
| Vision and recognition | 75 vision and 37 recognition tests passed. Total reported JVM tests across modules: 343, with zero failures. Opt-in export/benchmark work is not implied by this count. |
| Python safeguards | 10 release tests and 3 recognition data-guard tests passed. |
| Native libraries | Final AAB contained 20 libraries across four ABIs; all ten 64-bit libraries passed static ELF checks. The release APK native-alignment task and generated release manifest audit also passed. Binary bundle configuration, generated APK delivery and a 16 KB runtime remain to be qualified. |
| Build and lint | `check`, debug/release APK assembly, release AAB generation, native-alignment checks and release-note composition succeeded together. Lint reported zero errors, 21 warnings and 4 hints. |
| Physical phone | Updated the debug app on a OnePlus CPH2449 running Android 16 with 4 KB pages. Editing, persistence/reopening, answer checking, hints, tutor steps, 81 grid accessibility descriptions, landscape layout and controlled process-restart recovery passed. The disposable review puzzle was removed afterward. |

The earlier local verification AAB was generated at `app/build/outputs/bundle/release/app-release.aab`
(a path subsequently reused by newer builds), version 1.0.0 (code 1), SHA-256
`9CAEBB4C270D999F042681FF7AD6542103C0B6B827497C5D2AF02719258D0A19`.
That earlier build used the Android debug signing fallback; its release APK certificate
was verified as `C=US, O=Android, CN=Android Debug`. Its hash and certificate must not be
attributed to the newer stable-key-signed candidate 120 described above.

The lint warnings include a newer available SDK, legacy icon resources, ChromeOS ABI
coverage for the arm64-only local APK, deprecated window-width access and a Kotlin URI
helper suggestion. They do not invalidate the passing build, but zero lint errors is
not a claim that all UX and store qualification is complete.

The review at that stage called for the following qualification; consult the current
gates and device-test record for what has since been completed: run the signed
candidate through camera grant/deny/recovery, live focus and capture, repeated scans,
practice, sharing, large text and spoken TalkBack. Repeat the verified editing, history,
tutor, rotation and process-recovery flows against that same candidate. Measure frame
responsiveness and native memory over repeated captures. Run the candidate in a verified
16 KB environment and qualify Play-delivered signing/install/upgrade only after
distribution is authorized.

Recognition corpus scores are regression evidence, not independent OCR accuracy: the
shipped model was trained using corpus material. Evaluation on new puzzles and writers,
including pencil, colored ink, candidate notes, blur and difficult lighting, remains
pending. Group-held-out evaluation and its current limitations are documented in
[the recognition evaluation notes](recognition/README.md). The recognition test failure
was traced to a screenshot of the app's own annotated result, with colored overlays and
duplicate digits, rather than a source puzzle photo. It is now an explicit negative
fixture asserting refusal. Ordinary corpus accuracy baselines were not weakened.

This historical review did not approve publication. The current preparation likewise
requires the final artifact record and outstanding gates above before distribution.

## Internal testing sequence: only after explicit authorization

1. Confirm the approved AAB hash, version, track and tester-management scope.
2. Reconfirm the configured app-signing and upload certificates match the existing release
   identity. The authorized existing-key import is complete. For upgrade testing, retain
   an agreed lower-code Firebase installation or use a future higher-code Play build;
   the current phone already has 120. Do not uninstall or clear data to bypass a mismatch.
3. Upload that exact AAB to Internal testing and resolve package, signing, SDK or native
   warnings. This step is not performed by this checklist.
4. Add/select only user-authorized Google accounts/groups. Share the opt-in link only when
   authorized. Internal-test access is through the link; do not assume search discovery.
5. Install through Play and execute the attached test matrix against the exact version.
   A local APK install is useful preparation but does not verify Play delivery or signing.
6. Review available Play automated reports and device compatibility warnings. Availability
   of a pre-launch report varies by track/Console state; do not treat a missing report as a
   pass. Camera recognition also needs a human and a real printed puzzle.
7. Resolve failures, rebuild with a new version code when needed, and repeat affected checks.
8. Report internal-test results and request separate authorization for review submission,
   promotion or production rollout. Internal-test authorization does not authorize production.

The 12-testers/14-continuous-days production-access requirement is documented for newer
**personal** accounts. Do not assume it applies to Freevia's intended organization account
or that internal testing counts toward it. Follow the actual account's Console requirements.

## Official references checked 8 October 2026

- [Target SDK requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [Native alignment, bundle packaging and 16 KB runtime testing](https://developer.android.com/guide/practices/page-sizes)
- [Play App Signing and existing-key options](https://support.google.com/googleplay/android-developer/answer/9842756)
- [Internal, closed and open testing](https://support.google.com/googleplay/android-developer/answer/9845334)
- [Production testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465)

The initial browsing-tool fetch of the privacy URL failed. The website task subsequently
updated and verified the live policy via HTTPS on 8 October 2026; it now includes batch
diagnostics and the transferred GitHub repository link.

## Website presentation

The user requested and authorized the website update on 8 October 2026. The Sudoku page,
matching homepage description, optimized real-screen assets and privacy page are deployed
to https://freevia.org/sudoku-buddy/ through the existing Cloudflare Pages project `freevia`.
Deployment: https://521f67c7.freevia.pages.dev. It describes the app as coming to Google
Play; no store-download claim or app release was made.

Source: the `site/` directory in the Freevia website workspace.
Backups and the deployed static snapshot are retained in the release chat's private
website-review directory; workstation-specific paths are not part of the public handoff.
Previous production deployment: `8420621c-f16b-4a87-b785-b4021d0fd091`.
Desktop, 390px and 320px layouts, FAQ interaction, section anchors and asset paths were
checked. New screenshot assets total 231,038 bytes. Refresh screenshots if the final
camera/tutor UI changes materially; current visuals retain the documented build-116 provenance.

## Public repository presentation

Published README, docs/user-guide.md, .github/SUPPORT.md, the current privacy-policy
source and six image assets to freevia-org/sudoku-buddy on 8 October 2026.
Documentation commit: b42ee08ef89dfa8cb33901f0843aa686cfa6b3e0.
Repository description, website and topics were also aligned with the marketing page.
The commit uses [skip ci]; GitHub reported zero Actions runs for its SHA.

Only the ten reviewed documentation/image paths were included. Publication used GitHub's
Git data API; development files, local branches and indexes were not changed. The remote
main branch therefore includes this documentation commit ahead of the local base. Integrate
it normally before the next development push; do not reset or discard active work to sync it.
