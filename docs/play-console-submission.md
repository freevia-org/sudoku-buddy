# Sudoku Buddy — Play Console submission sheet

Current status (10 October 2026): signed candidate 1.0.0 (124) is built from merged main,
uploaded and saved in the existing Internal testing draft, which remains inactive. The track
has 2 of 3 setup tasks complete. Store-listing and declaration changes are not submitted for review;
**Send app for review** is disabled until required dashboard tasks are complete. The live
privacy policy says reports are not automatically added to a training corpus but may be used
as reviewed examples to improve recognition; repository policy says reports are not retained
in a corpus or used to train a model. Clarify whether long-term training use is intended, then
align consent, retention and saved Data safety answers before review or rollout. The copy
below is reference material; verify the exact current
Console values before submitting. Follow the [final-build checklist](play-release-readiness.md)
and its authorization gate.

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
  and corrections without a separate prompt. Each revision has a receipt. The published
  policy says each report is retained for up to 90 days. The Workers KV expiry is bounded.
  A local cleanup task now runs hourly and at user logon, starts when available, and retries
  once. The hourly interval can leave a copy for nearly an hour past expiry, and a powered-off
  computer cannot delete its local copies; those copies can remain longer until it returns
  and a user logs in. Verify all-copy deletion by the promised deadline before relying on
  this statement.
  A request from Settings receipts is intended to remove the matching server record and
  private working copies earlier. The endpoint is deployed. Complete physical-device upload,
  receipt, correction-update and deletion-request verification for the exact Play candidate
  before rollout.
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
before rollout; the public report-enabled policy is already live.

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
SHA-256 and application ID `org.freevia.sudokubuddy`. Candidate 1.0.0 (124) was built by
the successful GitHub candidate workflow and uploaded to the Internal testing draft; the
Console displays it as `1.0.0 (124) — Internal test`. The track remains inactive. The next
**Preview and confirm** step distributes it immediately to the selected testers, so do not
complete that step until the privacy/retention gates and tester scope are resolved. The
attached OnePlus has a local QA-signed version 122 and a separate v124-source debug package,
not a Play-delivered install. A Play-install upgrade test still needs an internal rollout
and must preserve existing app data; do not uninstall or clear storage to bypass a signing
mismatch.

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
