# iOS port feature coverage

This historical feature-coverage report does not establish identical behavior.
See [the platform consistency audit](platform-consistency-audit.md) for the later
screen-by-screen comparison, fixes, current evidence and remaining differences.

The app implementation now covers the requested Android feature areas. Local
compilation and corpus replay do not qualify real camera behavior or distribution.

| Area | Implemented iOS behavior | Evidence / qualification |
| --- | --- | --- |
| Recognition reports | Explicit analysis/training checkbox; optional automatic uncertain scans and correction revisions; deterministic ZIP; durable pending/accepted receipts | Storage/transport tests; synthetic mock service only |
| Report deletion | Journal deletion before DELETE; retry incomplete deletion; independent receipt history and tombstones | Failure/restart tests; complete consent/submission/restart/deletion UI passed |
| Bug reports | Reviewable GitHub browser draft; optional redacted diagnostics; public-disclosure copy; no automatic attachments or posting | URL bounds/escaping tests; no real issue posted |
| Sharing and diagnostics | Native photo/text share sheets; six local refused originals; explicit sharing preserves original bytes; deletion | Retention/restart/deletion tests; no automatic delivery |
| Tutor and strategies | Shared catalog, available examples, full routes, hint proofs, before/after candidates, dependencies, backward questions, forcing chains, practice and explicit placement | Shared JVM/native tests; route/proof/restart UI passed |
| Puzzle controls | Read/correct clues and handwriting; confidence/runner-up inspection; check answers; staged hint; read-only solution overlay | Existing and new SwiftUI workflows |
| History | Atomic puzzle/photo storage; current puzzle, review, progress, hints and study bookmarks restored; separate report receipts | 36 Swift storage tests; history/restart/deletion UI passed |
| Preferences | Explained/revealed hints; short-chain routes; automatic capture; optional automatic sharing with current consent | Durable preferences; automatic sharing defaults off |
| Camera | Native live preview, measured-grid framing, focus/light/motion gate, manual shutter, automatic capture setting, photo picker | Synthetic gate and preview tests; physical iPhone pending |
| Geometry | Native outline/cell geometry with shared rule alignment, perspective/surface pixels, cell preprocessing and CNN | Actual Android/iOS outputs match on all 118 images, including review markers; both accept 117 and refuse the annotated result screen |
| Localization/assets | 71 catalogs, language choice, RTL navigation with fixed board coordinates, Android palette/artwork, dark launch screen, offline notices | Resource checks; French, Bulgarian and Arabic UI passed |
| Privacy/release | Optional report collection declared, no tracking; upstream required API reasons; pinned native library; CI; unsigned Release archive | Archive succeeds; Apple Developer signing and TestFlight unavailable |

[Full corpus report](recognition/ios-corpus.md): 3,299/3,402 labelled cells have
both the correct digit and role. This trained-on corpus remains a regression set;
recognition still needs human review.

## Remaining qualification

- Physical iPhone: capture orientation, focus/exposure, shutter/motion, permissions,
  VoiceOver, Dynamic Type in real use, memory/thermal behavior and offline operation.
- Intended Apple Developer team: signed archive, final App Store privacy/policy
  reconciliation, distribution provisioning and TestFlight.

Neither hardware nor a signing identity is available in this session. No deployment,
publication, real report submission or real GitHub issue was performed.

## Final local validation, 19 October 2026

- Actual Android/iOS importer outputs match on all 118 corpus images, including both corrected digit errors and uncertainty markers.
- 22/22 iPhone UI workflows passed in a single complete run; 3/3 tablet workflows passed on a 13-inch iPad simulator. Four-stage hints, complex proof/rejection, correction, restart/history, localization, accessibility text size, preferences and feedback receipts/deletion are covered.
- 36 Swift storage/report tests and 14 resource/replay guards pass. Shared presentation contracts pass 55 tests each on JVM and native iOS; shared recognition/image/bridge and Android checks remain green.
- The final unsigned Release device archive and bundle audit pass. Signing, Apple upload validation and TestFlight remain separate.
- Normal launch through the actual system photo picker, Read, History, restart and four-stage hints were verified in Device Hub without test launch arguments. A synthetic sample is left ready on **Sudoku Buddy iPhone**. The earlier demo simulator disappeared from CoreSimulator during this work; its old history was not claimed restored.
- The [App Store packet](app-store/README.md) includes fourteen verified captures, listing copy, review sample/instructions, privacy/age/encryption drafts, updated policy/support sources and remaining publication/account/device gates. No live report, issue, website change, commit or store upload was performed.

See [current audit](platform-consistency-audit.md) and [machine-readable release evidence](app-store/verification.json). Logs and unsigned archive are under ignored `.tools/`. CI is configured for local-equivalent checks and release audits; no hosted CI run was dispatched in this session.
