# Sudoku Buddy private report service

Reports are stored once in the private `sudoku-buddy-training-examples` R2 bucket, keyed by their SHA-256 receipt. There is no automatic report expiry. The same object supports analysis and, when explicitly consented, training. `customMetadata.trainingConsent` is `true` or `false`; legacy R2 objects without this field were created only with training consent. Never train on a report marked `false`. Current Android submissions send `X-Sudoku-Training-Consent: yes`; older clients without that exact header remain analysis-only.

`POST /v1/reports` accepts `application/zip`, exact decimal Content-Length, a ZIP signature, and at most 20 MiB. Identical retries use R2 head checks and do not duplicate content. A subsequent explicit consented retry can upgrade an analysis-only object's permission in place. No public read/list route exists. Do not enable public bucket access or expiry lifecycle rules.

`DELETE /v1/reports/<64-character lowercase receipt>` removes the stored report. It returns `deleted` or `already_deleted` only after successful deletion. Failures return 503 with `deletion_incomplete` and `retry: true`. Each SQLite Durable Object serializes uploads, migration and deletion for its receipt. A durable deletion tombstone blocks future retries with 410. Operational state contains no report payload or submitter identity.

The account-wide limiter is 30 requests per 60 seconds, without IP keys. Worker observability remains disabled. Cloudflare may process network/security telemetry separately. ZIP signatures do not make archive contents safe to extract.

## Cutover from dual storage

The temporary KV `REPORTS` binding and scheduled migration are used only during cutover. Migration runs through the same per-receipt Durable Object queue, checks SHA-256 for KV-only reports, writes them to R2 marked analysis-only, and removes the KV entry only after successful R2 storage. Existing R2 objects are preserved. Deleted receipts cannot be restored. Remove the temporary migration trigger and KV binding after a complete KV inventory is empty (including the KV propagation window). No report files are exported to a workstation.

Local export remains disabled: downloaded copies cannot honor app receipt deletion. Historical retention helpers/tests apply only to cleanup of old local exports and are not the current server retention policy. Do not resume local review/export until receipt-complete deletion of every working copy is implemented and verified.

## Validation and deployment

Run `npm ci`, `npm test`, and `npm exec -- wrangler deploy --dry-run`. Deploy with `npm exec -- wrangler deploy` using the authenticated Cloudflare account. Test only synthetic ZIPs for live intake/retry/deletion checks. The endpoint is `https://sudoku-buddy-feedback.antoni-ivanov.workers.dev/v1/reports`. No credentials belong in the repository.
