# Release pipeline integration notes

Updated 10 October 2026. These checks prepare candidates; they do not approve distribution.

The manual Play candidate workflow takes a full 40-character commit SHA and an explicit
version code. It checks the checkout identity, requires the existing signing secrets,
verifies the AAB certificate, and packages evidence with checksums. Code 121 is supported;
confirm it exceeds all uploaded Play codes before using it. The workflow cannot infer
that fact from the repository or GitHub run number.

The integrated manifest contract requires CAMERA and INTERNET for camera processing and
voluntary private reports. Additional permissions still fail the audit. The candidate
workflow now generates all signed APK variants from the AAB, verifies each against the
existing certificate, and checks ZIP alignment with `zipalign -c -P 16`. The three generated
APK evidence reports are included by the existing explicit evidence allowlist. Temporary
signing passwords stay outside the checkout and artifacts and are removed after use.
This does not install APKs, prove 16 KB runtime behavior, or prove Play-delivered signing.

Firebase App Distribution was retired on 15 October 2026. CI builds and tests on every push,
but distributes nothing; Play releases are built only by `play-candidate.yml` from a
reviewed commit on main, and uploaded through Play Console.

Validation: all 20 release-tool tests passed; both workflow files parsed successfully,
and every shell step passed `bash -n`. These are static checks, not a completed hosted
candidate build. No Gradle build, app upload or publication was performed for these edits.

The integration chat owns the final coordinated commit, push and build. GitHub requires
the manual workflow to exist on the default branch before dispatch; publishing only the
preparation branch does not make a newly added workflow dispatchable. Ensure the default
branch has the opt-in distribution gate before bootstrapping the candidate workflow.
Select the immutable SHA only after the app integration and release tools are committed.

Remaining qualification: successful private-report consent/payload/receipt and deletion
tests; verified retention; matching live privacy policy and Play Data safety declarations;
final signed code-121 tests and certificate evidence; device/accessibility coverage; a
16 KB Android runtime; and Play-delivered installation/upgrade verification. Preserve the
sealed code-120 handoff3 as historical evidence and create a separate code-121 candidate.

## Branch consolidation, 15 October 2026

Every branch except `main` was removed. Ten were already contained in `main`
(seven by commit, three by an equivalent patch) and were deleted outright.

Eight more carried commits not reachable from `main`, all from 10 October, and
all conflicted with it. Each was checked file by file and found superseded, not
pending: the work had reached `main` through later pull requests, squashed or
reworked, and `main`'s copy was identical or newer. Their tips are kept as
`archive/codex/<branch>` tags so nothing is unrecoverable:

- `sudoku-release-prep`: 146 files; 100 identical on `main`, the rest newer there.
- `report-retention-cap`, `export-retry-lifecycle`: feedback-api retention and
  export files identical on `main`, or since refactored (`export-reports.mjs`
  lost 47 lines to `export-lifecycle.mjs`). `retention.test.js` on `main` is
  exactly both branches' tests combined.
- `release-readiness-final`, `update-release-readiness-20261010`,
  `release-status-docs`, `record-play-draft-124`: only the two Play handoff
  documents, at versions older than `main`'s.
- `submission-disclosure`: must not be revived. Its consent wording omits that
  shared reports are used for training; `main` says "analysis and training" in
  both the settings toggle and the post-submission message, matching the
  privacy policy and the Play Data Safety declaration.

To recover one: `git checkout -b <name> archive/codex/<name>`.
