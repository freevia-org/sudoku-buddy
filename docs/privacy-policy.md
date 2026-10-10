# Privacy policy for Sudoku Buddy

This is the source for the live [Sudoku Buddy privacy policy](https://freevia.org/sudoku-buddy/privacy). Last reviewed 10 October 2026. Verify the published page still matches this source before each app release.

Sudoku Buddy is published by **Freevia**.
Questions about this policy or the app's handling of data can be sent to
[info@freevia.org](mailto:info@freevia.org).

## The short version

Sudoku Buddy has no account, ads, routine usage analytics, or ad tracking. Camera processing
and puzzle history stay on your device. The app sends a puzzle report to Freevia only when
you submit it or enable optional automatic sharing for uncertain readings. A report contains
the straightened puzzle photo, original recognition results, uncertainty markers, and any
corrections recorded for that reading. A separate optional training choice controls whether
the report is kept as a private training example. Analysis-only reports expire within 90 days;
training examples are kept until you delete them by receipt. Reports are not public. Sudoku
Buddy does not currently train a model from submitted reports; learned changes in a model may
remain if model training is introduced later.

## What stays on your device

The app stores the following on your phone. Sharing is optional and is described below:

| What | Where | Why |
| --- | --- | --- |
| Straightened photographs of accepted scans | the app's private files directory | so a puzzle can be reopened from the history |
| The digits read from each puzzle, reading details and your corrections | the app's private files directory | so the reading and your progress survive closing the app |
| The last few photographs the app *refused* to read | the app's external files directory, or private storage if external storage is unavailable | so you can look at what went wrong, or choose to share it for support |
| Your settings | the app's private preferences | so they persist |
| Submission receipts for reports you sent | the app's private preferences | so you can identify a report revision if you ask Freevia to delete it |

Refused photographs normally live in the app's own folder on external storage. Access
through a file manager or USB depends on the Android version and device. If that storage
is unavailable, the app uses its private files directory instead. Either location supports
the explicit sharing actions described below.

The app keeps at most six refused photographs and discards the oldest beyond that. You can
delete any of them from the puzzle history at any time.

## Network connection and optional report submission

Scanning, recognition, solving and tutoring run on the device. Report submission requires
an internet connection and the Android `INTERNET` permission. Other app functions remain
available offline. Only report submission needs a network connection.

Opening a website link or choosing a receiving app in the share sheet hands that action
to another app. That app may use the internet under its own permissions and policies.

Sudoku Buddy does not run routine usage analytics, crash reporting, or ad tracking.
If you choose to submit a puzzle report, Freevia can analyze its recognition results and any
corrections to improve recognition. You can separately choose whether to retain the report as
a private training example. This report processing is not routine tracking of app use.

## Sharing is your choice

When you choose Share, the app can hand a saved puzzle photograph or a refused scan to
another app, such as a mail client or messaging app. Send diagnostics can hand over the
retained refused photographs together with a text report, or the report alone if there
are no refused photographs. The report includes the app version, date, phone manufacturer
and model, Android version, and scan outcomes. You choose the receiving app in Android's
share sheet. File access is read-only and limited to the photographs included in that
share action.
These share-sheet actions send nothing until you choose a receiving app. If you choose to
send materials to Freevia for support, Freevia receives the information you include. Do not
include photographs or other information you do not want the recipient to see. The separate
optional automatic-submission setting for uncertain readings is described below.

The receiving app's policy also applies to any copy it receives. Sharing a file does not
give Sudoku Buddy control over the recipient's copies. If Freevia is the recipient, the
support-sharing description above still applies to what you send to Freevia.

### Puzzle reports for recognition analysis

You can submit an uncertain reading from the puzzle screen. Submitting is optional and
requires your action. A separate unchecked choice on the submission dialog lets you keep
that report as a private training example. In Settings, another separate training choice can
apply to future reports shared automatically. Turning on **Share automatically when uncertain**
sends the currently open uncertain reading and future uncertain readings and corrections
without asking each time; it does not by itself give training consent. Both automatic choices
are off by default and can be turned off in Settings.

A report includes the straightened square puzzle photograph, app version, original and
current digit grids, each cell's recognition classification and confidence, uncertain cells,
grid geometry, and the sequence of corrections with their times. This can show handwritten
marks or other details visible on the page. Do not submit a page containing information you
do not want Freevia to review.

Reports are private and are not posted publicly. Unless you choose the separate training
option, each uploaded revision is held in a private Cloudflare Workers KV analysis queue and
expires within 90 days. A training choice applies only to that reading; its later correction
revisions keep the same choice. Each revision has its own receipt. Analysis-only revisions
are not copied into the training archive. A report opted in to training is kept in a private
Cloudflare R2 archive until you delete it from **Submission receipts**. A successful deletion
removes that revision from Freevia's KV and R2 service storage. Sudoku Buddy does not
currently train a model from submitted reports; if a model is trained on an example later,
deleting that example may not remove learned changes from the model.

The app keeps receipts in Settings, even if you delete a puzzle from History. Open
**Submission receipts** to delete a submitted revision. Corrected revisions have separate
receipts and must be deleted separately. Deleting a puzzle from History removes its local
copy but does not delete reports already sent. Cloudflare provides storage and request
infrastructure on Freevia's behalf and may process network/security telemetry under its own
terms. The report record does not store your identity or source IP.

### GitHub feedback drafts

When you choose Report a bug or idea on GitHub and confirm Open GitHub, the app opens
a GitHub issue URL in an external browser or another application that handles the link.
The URL includes a prefilled title and report text with the app version/build number,
phone manufacturer/model, Android version and recent scan diagnostics. GitHub receives
this text when the link is opened, even if you do not submit the issue. No photograph
is included in the URL or uploaded by Sudoku Buddy.

You sign in on GitHub, can edit the draft, choose any screenshots to attach there, and
decide whether to submit it. Submitted issues and attachments are public. GitHub's own
privacy policy applies to its website and account. Use email for support examples you
prefer to share privately.

## Camera processing

The app asks for camera access because reading a puzzle from a printed page is what it
does. Camera preview frames are processed on the phone and are not saved. A captured
photograph can be saved as a puzzle or retained as a refused scan. Capture can be manual
or automatic when that setting is enabled.

## Deleting your data

You can delete saved puzzles and retained refused photographs from History. Uninstalling
the app removes its app-specific storage, including locally retained photographs and
settings. Deletion from the app does not delete copies you already shared or copied elsewhere.

The app disables Android backup and explicitly excludes its storage from cloud backup
and device transfer. There is no cloud history or automatic restoration to another phone.

## Children

Sudoku Buddy is intended for a general audience aged 13 and over. Reports are sent only when
a user submits them or enables optional automatic sharing. The report may include handwriting
and other details visible in the photographed puzzle. The same choices and disclosures apply
to all users.

## Changes to this policy

If this policy changes, the date at the top changes with it, and the history of this file
is public in the repository it lives in.

## Contact

Email [info@freevia.org](mailto:info@freevia.org). You can also raise a public technical
issue at [GitHub Issues](https://github.com/freevia-org/sudoku-buddy/issues). Issues and
attachments there are public, so include only information you intend to publish.
