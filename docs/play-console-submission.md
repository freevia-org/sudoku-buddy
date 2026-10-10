# Sudoku Buddy — Play Console submission sheet

Current status snapshot (10 October 2026): PR #18's training-consent source is merged.
For any packaged candidate, read `release-record.json`, `SHA256SUMS.json` and `evidence/`
for its exact source, version, AAB hash, signing and validation provenance. Do not infer
package identity from this prose or from version code alone. Finalize and merge all reviewed
release inputs before using the non-distributing workflow, then verify the resulting package.

Candidate 124 remains the only bundle in the last audited inactive Internal testing draft
(2 of 3 tasks complete); no rollout occurred. Store-listing and declaration changes remain
unsubmitted. **Send app for review** is disabled until dashboard tasks are complete.
Production is inactive with 0 of 5 tasks complete. Follow
[release readiness](play-release-readiness.md) for evidence, limits and gates.

The text below is repository-side preparation, not proof of saved Console values. The last
live audit found blanket 90-day retention in the saved listing and old email deletion
instructions in the public policy. Publish the matching policy and obtain appropriate scope before saving
listing changes. Source merge does not provision training storage or deploy the new Worker.

Originally prepared 8 September 2026; refreshed for the merged training-consent source on
10 October 2026. Local edits do not update Play Console or the public website.

## Create app

| Play Console field | Answer |
| --- | --- |
| Default language | English (United States) — `en-US` |
| App name | Sudoku Buddy |
| App or game | App |
| Free or paid | Free |
| Support email | `info@freevia.org` |
| Package name | `org.freevia.sudokubuddy` |

Account setup is already saved. On 8 October, the user-authorized existing-key import
was completed through the Console's Java-keystore/PEPK encrypted-key flow. The downloaded
App signing key certificate and registered Upload key certificate both match the existing
Firebase/local SHA-256:
`A5:10:D8:2B:87:E7:06:9B:53:D5:20:BE:CA:91:5B:35:2A:E9:6C:EA:85:0C:68:CA:00:61:17:E3:5D:75:D3:E5`.
No AAB/APK, tester changes or rollout accompanied that operation. Actual Play-delivered
installation, certificate and upgrade/data retention remain untested; follow
`play-release-readiness.md`. Do not repeat enrollment or accept agreements as a routine
preparation step.

## Main store listing

| Field | Answer or file |
| --- | --- |
| App name | Sudoku Buddy |
| Short description | Scan and check paper Sudoku, with clear hints when you get stuck |
| Full description | Use the text under **Full description** below |
| App icon | `docs/store/icon-512.png` |
| Feature graphic | `docs/store/feature-graphic-1024x500.png` |
| Phone screenshots | Five files listed in the release package's `store/screenshots/README.md` |
| App category | Education |
| Tags | Sudoku; Puzzle; Education, where those tags are offered |
| Support email | `info@freevia.org` |
| Website | `https://freevia.org` |
| Privacy policy | `https://freevia.org/sudoku-buddy/privacy` |

The feature graphic was created with generative image tooling. If Play Console shows its
asset-level AI-content checkbox, enable the AI label for that graphic. The app icon is
derived from the app's existing artwork and real in-app screenshots are not AI-generated.

### Reference full description — verify against the current Play listing before review

Sudoku Buddy is a camera companion for standard 9x9 Sudoku puzzles in newspapers, books
and magazines. It helps you check and understand the puzzle already on paper.

Point your camera at a printed grid. Sudoku Buddy reads the printed clues and handwritten
answers, then lets you correct a digit or mark it as printed or handwritten. Review the
reading first: lighting, pencil marks, erasures and handwriting can affect recognition.

When the printed clues define one solution, use Check to see which handwritten answers
are right and which need another look. With explained hints, reveal a region, a technique,
a square and then its digit at your own pace.

Explore the Tutor's full route or browse examples of a technique. Inspect candidate changes
with Before and After, revisit earlier reasoning, or use Try it yourself on supported steps.
Some difficult positions need solver-assisted help; the app labels it when a detailed proof
is unavailable.

Use Solve to view a solution. If the clues allow several answers, browse a limited set of
them and inspect their differences. If no solution exists, the app can suggest printed
digits to review. Reopen saved scans and corrections from your on-device history.

Private by design:

- Camera processing happens on your phone
- No account
- No ads
- No routine usage analytics or ad tracking
- Puzzle reports go to Freevia only when submitted, or when the user has enabled optional automatic sharing for uncertain readings

You can choose to submit a puzzle photo with its recognition results and corrections for
private analysis to improve reading. Automatic sharing is optional. Training is a separate
optional choice: analysis-only reports expire within 90 days; opted-in training examples
stay private until you delete them through Submission receipts. Each corrected revision
has its own receipt. Deleting an example may not reverse learned changes if a model is
trained on it later. Scanning, checking and tutoring work offline and remain available
without sharing; submitting or deleting a report requires an internet connection.

Sudoku Buddy is for people who enjoy solving on paper and want a second pair of eyes, not a
replacement game.

## App content declarations

### Privacy policy

- URL: `https://freevia.org/sudoku-buddy/privacy`
- The URL is public, active, non-PDF, and also shown inside the app's About screen.

### Ads

- **Does your app contain ads?** No.

### App access

- **Are all app features available without special access?** Yes.
- No account, login, membership, location restriction, or access code is required.
- Reviewer note if a text box is offered:

  > All functionality is available without an account or login. Grant camera permission,
  > point the camera at a printed Sudoku. After scanning, every recognized digit can be
  > corrected before the user checks handwritten answers, requests a hint or views the
  > solution. Recognition runs on the device and history is local. Users can optionally send
  > an uncertain-reading photo and results for analysis. The manual submission dialog has a
  > separate unchecked training choice; Settings has independent automatic submission and
  > training choices, both off by default. Analysis-only reports expire within 90 days;
  > training examples remain private until receipt deletion. Each corrected revision has
  > its own receipt. Settings → Submission receipts deletes the matching service record.
  > Verify this flow against the final app and deployed service before supplying this note.

### Target audience and content

- Target age groups: **13–15, 16–17, and 18 and over**.
- The app is not specifically designed for children under 13.
- Store presence does not intentionally appeal to children.
- The app contains no advertising.

These selections describe the intended audience, not whether younger people are capable
of using Sudoku. Do not select an under-13 group unless Freevia intentionally chooses to
enter the Families programme and reassesses the listing against those policies.

### Data safety

- The current release candidate has `INTERNET` permission and sends an opt-in report to
  Freevia over HTTPS. Do not reuse the earlier offline-only candidate's declarations.
- Camera preview frames are processed on the device and are not saved. Captured puzzle
  photographs, recognized digits, corrections, history and settings are stored on the
  device, except for material the user deliberately shares. Backup and device-transfer
  rules exclude the app's data.
- A report includes the straightened photo, original/current grids, per-cell readings and
  confidence, uncertainty, geometry and corrections with timestamps. Submission is optional.
  Automatic sharing explains its payload before enabling it and sends the current uncertain
  reading, future uncertain readings and later corrections without another prompt each time.
- Training requires a separate unchecked manual choice or separate automatic training
  setting; automatic submission alone is not training consent. Analysis-only revisions
  retain their original 90-day KV expiry across retries. Consented examples also enter a
  private R2 archive until receipt deletion. Do not migrate old analysis-only reports into
  training without new consent. Corrections preserve the reading's choice and each revision
  has a separate receipt.
- Settings receipt DELETE is designed to remove the matching service KV and R2 objects;
  it does not remove local exported copies or undo influence learned by a future model.
  The privacy policy must explain these limits accurately. Keep exports disabled until
  historical copies are inventoried and receipt-complete deletion is proven.
- Backend provisioning/deployment and real-device upload, correction, retry, opt-out and
  deletion qualification remain gates. Source merge does not create the private R2 bucket,
  apply the Durable Object migration or deploy the consent-enabled endpoint. Follow
  `feedback-api/README.md` for legacy expiry inventory and cutover validation.
- The app has no account-creation mechanism.
- Privacy policy: `https://freevia.org/sudoku-buddy/privacy`.

Google defines collection for this form as transmitting data off the user's device. The
current report flow is first-party collection by Freevia; it is separate from optional
third-party share-sheet actions. For this release candidate, declare the report photo under
**Photos**, recognition readings and corrections under **Other user-generated content**,
automatic/manual report activity under **App activity / Other actions**, and submitted
diagnostic fields under **App info and performance / Diagnostics**. Mark collection
optional, non-ephemeral, and for **Analytics** (improving recognition); declare no sharing
only while Cloudflare acts solely as Freevia's service provider under Google's current
definition. Verify these answers against the exact uploaded payload and service behavior.
The app has no account, routine usage analytics, crash reporting or ad tracking; report
analysis is limited to reports the user submits or has elected to submit automatically.
Verify the live privacy policy and saved Data safety answers against the exact app payload
before rollout. The last live policy audit still had universal 90-day retention and email
request wording; publishing the merged two-choice policy is a prerequisite. Longer training
retention alone has no separate duration selector in Data Safety. Preserve optional collection
only if all users can decline it; verify actual purposes, any new identifiers, providers,
encrypted transport and working deletion before retaining the saved answers.

### Content rating questionnaire

Use an email address monitored by Freevia and select the general utility/education app
category offered by the questionnaire. For the current build:

- Violence: No
- Fear or horror: No
- Sexual content or nudity: No
- Profanity or crude humor: No
- Drugs, alcohol, or tobacco: No
- Gambling or simulated gambling: No
- User-generated content: No
- Users communicating or exchanging content: No
- Location sharing: No
- Purchases or paid digital goods: No
- Ads: No
- Unrestricted web access: No

Submit the questionnaire and retain the IARC certificate email. The rating authority,
not this checklist, assigns the final regional ratings.

### Other declarations

- News or magazine app: No
- Government app: No
- Financial features: None
- Health features: None
- VPN service: No
- Account creation: No
- Generative AI app: No. The app recognizes a fixed Sudoku grid and applies deterministic
  solving techniques; it does not generate open-ended text, images, audio, or video from
  user prompts.
- High-risk permissions declaration: none expected. `CAMERA` is the only requested runtime
  permission and is necessary for the app's core scan function.

## Countries and availability

- Make the app available worldwide except where Google Play or applicable law prevents it.
- The app requires Android 8.0 or later and uses a rear camera. Review the final bundle's
  supported devices and native architectures in Play Console against the tested devices.
- The app is free and has no in-app products or subscriptions.

## Testing and release

1. Complete consent, service lifecycle, privacy/listing alignment and final-candidate
   device qualification in `play-release-readiness.md`.
2. Finalize and merge reviewed release inputs, then build using `play-candidate.yml`,
   the final full reviewed SHA and a new version code above every Play upload. The workflow
   validates a positive code and SHA ancestry but does not query Play's highest code.
3. Download `sudoku-buddy-play-candidate-<code>` and
   `sudoku-buddy-candidate-checks-<code>`. Verify the package inventory, exact AAB hash,
   source/code/package, stable certificate, tests/lint and bundle/APK alignment evidence.
   Initial training workflow 38017589369 specifically built code 125 before the handoff
   refresh and embeds older documents; that run is superseded as a publishing handoff.
   A later package's identity and evidence come from its own sealed records. Preserve
   historical inventories; do not rewrite their embedded files or checksums.
4. With authorized draft-upload scope, replace the superseded bundle in the existing
   internal draft, update its name/notes accurately, save as draft and inspect that new
   candidate's preview diagnostics. Current release notes describe analysis only; revise
   them for the final training candidate before packaging. Do not click **Save and publish**.
5. Obtain separate authorization for the exact AAB and selected internal testers before
   distribution. Then verify Play-delivered signing, installation and upgrade/data retention
   and inspect any available pre-launch report. Preserve existing app data.
6. Production remains inactive: select countries/regions, create a release, preview/confirm,
   send for review and publish are five separate later tasks. Managed publishing is off;
   review and publication require the owner's release scope.

### Historical candidate-124 Console evidence

Candidate 124 (`1.0.0`, package `org.freevia.sudokubuddy`) was uploaded and saved as
`1.0.0 (124) — Internal test`, candidate 124 only. Its track remains inactive at the last
Console audit. The candidate-only preview showed zero errors and two warnings (no mapping
and no native debug symbols). Entering preview did not distribute it. The separate final
**Save and publish** button says changes publish immediately; it was not clicked. These
checks do not validate candidate 125 or its replacement. Candidate 124 lacks PR #18's
training consent and must not be used for the new training release.

At the candidate-124 audit, the OnePlus had a QA-signed release-package v122 and a
separate v124-source debug package, not a Play-delivered install. The later v125-source
physical smoke is recorded separately in `play-release-readiness.md`. Do not uninstall or clear storage to bypass a signing mismatch.
The earlier camera, text-size and emulator checks remain partial historical evidence.

## Account and policy details to confirm in Play Console

The original registration checklist below is retained for reference. Earlier handoffs
report account/app setup complete; do not treat these as newly discovered missing items.

- D-U-N-S number and organization verification
- The organization's main telephone number, matching public or D&B records
- A public, OTP-capable developer telephone number
- A private contact telephone number for Google
- One-time developer registration payment
- Final answers to any new declarations Play Console adds after this checklist was prepared
- Reconcile the updated listing and public privacy policy with the final app, including
  voluntary support reports, their retention and deletion handling, and the Data safety answer
