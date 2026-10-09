# Sudoku Buddy — Play Console submission sheet

Current status (10 October 2026): app creation, listing, privacy URL, declarations and IARC
rating were previously complete per the task handoff. The release-preparation branch implements
opt-in report intake, which changes network access and data handling. Reconcile the saved listing, privacy policy and Data safety
answers against the final build and verified service before any AAB/APK upload or testing rollout; follow
[the final-build checklist](play-release-readiness.md), including its explicit authorization
gate and final privacy-text reconciliation. The answers below remain reference copy.

Originally prepared 8 September 2026; product copy reviewed against the local source on
8 October 2026. Local edits do not update the saved Play Console listing or public website.

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

### Proposed full description — not yet saved to Play Console

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
private analysis to improve reading. Automatic sharing is optional. Scanning, checking and
tutoring work offline; submitting a report requires an internet connection.

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
  > solution. Recognition and puzzle history stay on the device. Users can optionally send
  > an uncertain-reading photo and its recognition results to Freevia for private analysis.

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
- A report includes the straightened puzzle photograph, original/current grids, per-cell
  recognition results and confidence, uncertainty markers, geometry, and user corrections
  with their times. Manual submission is user initiated. The optional “Share automatically
  when uncertain” setting first explains the photo and results that will be sent; enabling
  it sends the currently open uncertain reading and later sends future uncertain readings
  and corrections without a separate prompt. Each revision has a receipt. Its KV object and
  all Freevia-controlled export/review copies are deleted within 90 days of upload; a request
  from Settings receipts removes the matching KV object and private working copies earlier.
  The endpoint is deployed. Complete physical-device upload, receipt, correction-update and
  deletion-request verification for the exact signed Play candidate before rollout.
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
Complete physical-device verification and publish the matching privacy policy before rollout.

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

1. Upload the signed `app-release.aab` to **Internal testing** first.
2. Add Freevia-controlled tester accounts and verify installation, camera permission,
   scanning, editing, hints, history, sharing, and uninstall behavior.
3. Review Play's automated pre-launch report.
4. Promote the tested bundle to a closed or production track as appropriate for the
   organization account.
5. Use `docs/play-release-notes-en-US.txt` for the first release notes.

Use the verified signed candidate recorded in `play-release-readiness.md`, checking its
SHA-256 and application ID `org.freevia.sudokubuddy` before upload. Candidate 1.0.0 (120)
was built locally with the stable release key; the prepared GitHub candidate workflow has
not been run. Upload and rollout require explicit authorization for the build and track.
The current test phone already has 120; an actual upgrade test needs an agreed same-key
lower-version baseline or a future higher-code Play build. Preserve existing app data;
do not uninstall or clear storage to bypass a signing mismatch.

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
