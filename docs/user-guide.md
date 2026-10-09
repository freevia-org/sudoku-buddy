# Using Sudoku Buddy

Sudoku Buddy helps you check and understand the standard 9×9 Sudoku already on your
printed page. It is being prepared for its first Google Play release; the screens below
show an existing development build.

[Website](https://freevia.org/sudoku-buddy/) · [Repository overview](../README.md) ·
[Privacy](https://freevia.org/sudoku-buddy/privacy)

## 1. Scan the page

Open the camera and grant camera permission. Place the whole grid inside the capture
guide, including all nine rows and columns. Use even light, keep the page as flat as
practical, and hold the phone steady. Printed clues and your existing handwritten
answers are both part of the reading.

<img src="images/01-scan.webp" alt="Camera guide around a printed Sudoku with handwritten progress" width="240">

## 2. Review what was read

Compare the recognized grid with the photograph. Review marked or uncertain squares
first, then check the rest. Tap a square to correct a digit or its interpretation when
needed. Handwritten answers must remain distinguishable from the printed clues.

Pencil marks, erasures, crossings-out, glare and unusual handwriting can cause errors.
Correct the reading before asking the app to evaluate the puzzle.

<img src="images/02-puzzle.webp" alt="Recognized puzzle ready for review and correction" width="240">

## 3. Choose your help

**Check:** review your handwritten answers. Correct entries are highlighted green;
incorrect entries are highlighted red. If the result is unexpected, revisit the reading
and the printed clues first.

**Hint:** find a next deduction. The app names the technique and highlights the relevant
cells. Reveal further help at your own pace; pause and try the deduction on paper before
continuing to the answer.

**Solve:** view the solution when that is the level of help you want. An invalid or
ambiguous reading may need correction before the app can give a useful result.

<img src="images/03-check.webp" alt="Correct and incorrect handwritten answers highlighted on a puzzle" width="240">
<img src="images/04-hint.webp" alt="A Naked single hint highlighting its target cell" width="240">

## 4. Come back later

Open history to revisit a saved scan and its corrections. You can delete accepted puzzles
and retained unsuccessful scans from the history. Your history stays on the device;
uninstalling the app removes its app-specific data.

<img src="images/05-history.webp" alt="History of previously scanned Sudoku puzzles" width="240">

## When a scan is difficult

| What you see | What to try |
| --- | --- |
| The grid is not found | Fit the whole grid in the frame and reduce nearby clutter. |
| Glare or blurred digits | Change the light or camera angle, then hold steady. |
| Digits or printed clues are wrong | Review and correct the recognized squares before checking or requesting hints. |
| A hint seems inconsistent with the page | Recheck the reading, especially printed clues and handwritten answers. |
| The app still cannot read the page | Keep solving on paper and report the case if you want help investigating it. |

## Sharing and support

The release-preparation build can submit an uncertain reading directly to Freevia for
private analysis. The report includes the straightened puzzle photo, recognition results and
corrections. Automatic sharing is optional and off by default; enabling it sends the open
uncertain reading if there is one, then future uncertain readings and corrections. This build has not completed
device qualification, so the feature is not yet part of the public release. See the draft
privacy policy for its data handling; the public privacy page still describes the current
released build.

You can deliberately share a saved puzzle photograph or an unsuccessful scan through
Android's share sheet. Send diagnostics can include retained unsuccessful scans and a
text report containing app version, date, phone manufacturer/model, Android version and
scan outcomes. Choose the receiving app and recipient yourself.

Email [info@freevia.org](mailto:info@freevia.org) for support. If you report a bug in
[GitHub Issues](https://github.com/freevia-org/sudoku-buddy/issues), the report and its
attachments are public. Include only materials you intend to make public.

The [privacy policy](https://freevia.org/sudoku-buddy/privacy) explains local storage,
retention, deliberate sharing and deletion.
