# Sudoku Buddy — Play Console submission sheet

Current status snapshot (10 October 2026, after renewed release work): main is clean at
`786402d57b01bc623cc6940492ff75deb658e96e`. Candidate workflow [38038748245](https://github.com/freevia-org/sudoku-buddy/actions/runs/38038748245)
passed for code 126 from that commit; its AAB SHA-256 is
`AF1CA3B6E979745213DF54D55AFA84A518DC5AA999B8ABC2A1014A82425125F0`. It is still an offline
artifact and contains privacy text from before today's location-disclosure update; rebuild
code 126 from the reviewed policy update before distribution. The live policy at
`https://freevia.org/sudoku-buddy/privacy/` now describes Cloudflare's possible IP-derived
approximate-location processing and its service-provider role. Data Safety answers are saved
as a draft in Publishing overview: optional approximate location for security; optional Photos,
Other user-generated content and Other actions for Analytics; no data shared with third parties;
no Diagnostics. The listing remains a draft. Internal testing is still inactive with candidate
125 in the draft track and 2 of 3 setup tasks complete. Real Android submission/deletion and
Play-delivered installation are not yet verified. Production remains inactive at 0 of 5 tasks.

The full description and Data Safety answers are saved in Play Console as drafts. Neither has
been sent for review. The live privacy policy is published and verified. The Worker has passed
synthetic upload/deletion checks; real app submissions remain unverified. This status snapshot
supersedes older release-state notes retained below for history.

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
private recognition analysis and training. One optional setting automates submission of
future uncertain readings. Training copies stay private until you delete them through
Submission receipts. Each corrected revision has its own receipt. Deleting an example may
not reverse learned changes if a model is trained on it later. Scanning, checking and tutoring
work offline and remain available without sharing; submitting or deleting a report requires
an internet connection.

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
  > an uncertain-reading photo and results for analysis and training. Submitting is an
  > explicit opt-in; the dialog has one optional checkbox to automate future submissions.
  > Automatic submission is off by default. Training examples remain private until receipt
  > deletion. Each corrected revision has its own receipt. Settings → Submission receipts
  > deletes the matching service record.
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
- The report ZIP includes the straightened photo and a manifest with the app version,
  original/current grids, uncertain-cell indices, 81 per-cell readings (ink, digit,
  confidence, runner-up and role uncertainty), board-line coordinates, and correction events
  (cell, old/new value and source, and timestamp). It does not include device model, crash
  logs, performance metrics, or a log of settings toggles. Submission is optional.
  Automatic sharing explains its payload before enabling it and sends the current uncertain
  reading, future uncertain readings and later corrections without another prompt each time.
- Submitting manually opts the report into recognition analysis and private training.
  Enabling automatic submission opts future uncertain readings into the same processing;
  it is off by default and requires confirmation. Keep the report photo, readings, corrections,
  training retention and deletion behavior accurately described. Older automatic analysis
  preferences are turned off on upgrade unless training had also been enabled; users can
  enable the new behavior after reviewing its disclosure. Each revision has a separate receipt.
- Settings receipt DELETE is designed to remove the matching service KV and R2 objects;
  it does not remove local exported copies or undo influence learned by a future model.
  The privacy policy must explain these limits accurately. Keep exports disabled until
  historical copies are inventoried and receipt-complete deletion is proven.
- Backend provisioning/deployment is complete: private R2 and the consent-enabled Worker are
  live. Synthetic consented and analysis-only uploads followed by receipt deletion passed.
  Real-device upload, correction revisions, retry, opt-out and app-driven deletion remain
  gates. Keep the website, listing and Data Safety answers aligned before distributing a
  fresh candidate.
- The app has no account-creation mechanism.
- Privacy policy: `https://freevia.org/sudoku-buddy/privacy`.

Google's [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
defines collection for this form as transmitting data off the user's device. The saved draft
declares the report photo under **Photos**, puzzle/readings/corrections under **Other
user-generated content**, correction actions under **Other actions**, and Cloudflare's
possible IP-derived approximate location under **Location** for security. All are optional;
photos and puzzle content are non-ephemeral and used for **Analytics** (recognition improvement),
while approximate location is non-ephemeral and used for fraud prevention/security. Diagnostics
is not selected because the manifest carries puzzle-recognition outputs, not app/system
diagnostics. No third-party sharing is declared because Cloudflare operates as Freevia's
service provider. The policy and saved draft now align on that processing. Verify the exact
candidate payload, all SDKs and real service behavior before sending the draft for review.
Settings toggles are not uploaded; the request may include only a one-bit training-consent
header. The app has no account, routine usage analytics, crash reporting or ad tracking.
Longer training retention has no separate duration selector in Data Safety.

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

1. Merge the current privacy-policy/Data Safety documentation update and build a fresh code-126
   candidate from that reviewed revision. Code 126 is still free because no version-126 bundle
   has been uploaded to Play.
2. Download `sudoku-buddy-play-candidate-126` and `sudoku-buddy-candidate-checks-126`. Verify
   the package inventory, exact AAB hash, source/code/package, stable certificate, tests/lint and
   bundle/APK alignment evidence. Preserve historical artifact inventories unchanged.
3. Replace candidate 125 in the inactive internal-test draft with the verified code-126 AAB,
   inspect its release notes and preview, then save and roll out to the existing authorized
   internal testers. The user has authorized this release work and device verification.
4. Install the Play-delivered build on the attached OnePlus and verify signing, install/upgrade
   behavior, report submission, correction revisions, retry/opt-out and receipt deletion using a
   synthetic test puzzle. Preserve existing user data.
5. Reconcile any new Play Console declaration or review feedback against the verified app,
   service and live policy. Production remains inactive; complete its remaining track tasks and
   send the release for review under the user's explicit Play Store publication request.

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
