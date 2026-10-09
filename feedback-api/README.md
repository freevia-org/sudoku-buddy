# Sudoku Buddy private feedback intake

This is the private opt-in upload endpoint for Sudoku Buddy report ZIP files. It is deployed separately from the Android build at the URL configured in the release build. The endpoint has no read/list API and returns only an opaque SHA-256 receipt.

## Storage and limits

- `POST /v1/reports` accepts only `application/zip` with a decimal `Content-Length` that exactly matches the streamed body.
- The body is read incrementally and stopped as soon as it exceeds the declared length or the 20 MiB hard cap. It is buffered only after bounded collection so it can be hashed and written to Workers KV.
- The first four bytes must match a ZIP local-file, empty-archive, or spanning signature. This is a quick format check, not a full ZIP safety scan; moderators must treat all report contents as untrusted.
- The SHA-256 digest is the receipt and key (`reports/<sha256>.zip`), making identical submissions idempotent. No submitter name, email, app installation ID, timestamp, or source IP is stored in a separate record.
- Each new report revision receives the shared 90-day `expirationTtl` in `src/retention.js`. Cloudflare documents that an expiring key is deleted when its expiry is reached. The KV key listing exposes the absolute expiration; the exporter uses that timestamp as the deadline for every local copy and refuses keys without a valid expiry or with an expiry beyond the 90-day limit. Every corrected revision has a distinct receipt and its own deadline.
- The KV account value limit is 25 MiB; this service caps values at 20 MiB. This queue is intended for a bounded, low-volume pilot. KV is eventually consistent and does not provide an atomic compare-and-set; identical simultaneous submissions still converge on the same content-addressed key, but only one request may report `accepted`.
- The rate limiter uses a single account-wide counter (30 calls per 60 seconds), so it never needs a client IP key. It is deliberately coarse and shared by all users; Cloudflare describes this binding as a soft limit, not a precise abuse-control or quota system.

## Create and deploy

Prerequisites: Node.js 20 or later, Wrangler 4.36.0 or later (for `ratelimits`), a Cloudflare account with Workers and Workers KV enabled, and an account-scoped API token that can deploy this Worker and access only the intake namespace. No credentials belong in this folder or repository.

The private namespace and Worker are already created and deployed. The endpoint is `https://sudoku-buddy-feedback.antoni-ivanov.workers.dev/v1/reports`; the private KV namespace ID and rate-limit configuration are recorded in `wrangler.toml`. A synthetic ZIP was accepted, an identical retry returned the same receipt, and the synthetic object was deleted. Do not send corpus or personal test photos during infrastructure checks. Future deployments require an authorized Wrangler session and a review of the current account configuration.

## Private review and export

There is no public report retrieval route. Before any report is reviewed, Freevia must designate one reviewer-owned workspace on an encrypted, non-synced, non-backed-up machine, assign an owner, and install a daily scheduled run of the expiry cleanup below. Keep the workspace outside this repository. Do not export to OneDrive, Google Drive, Dropbox, email, shared drives, removable media, another person's machine, or any other location. Only the designated reviewer uses it; every photo, ZIP, extracted item, annotation, or other working copy must remain inside that receipt's folder. Do not add report content or derivatives to a source corpus, training set, model weights, or another persistent system. Review findings may inform code changes after the report materials are removed.

A trusted reviewer with a Cloudflare API token scoped only to read KV keys/values may export the queue. Set `CF_ACCOUNT_ID`, `CF_KV_NAMESPACE_ID`, and `CLOUDFLARE_API_TOKEN` in that machine's process environment. Point the exporter only at the designated, empty private workspace:

```sh
node scripts/export-reports.mjs /private/path/sudoku-buddy-reports
```

The exporter lists only `reports/` keys, requires each key's absolute KV expiry, checks that the expiry is within the 90-day cap, verifies the ZIP digest/signature, and creates a per-receipt directory with a `retention.json` deadline. It refuses a non-empty unmanaged destination and does not overwrite existing exports. Keep all review and derived working files in that receipt directory; delete them as soon as review is complete. Do not train on or persist report-derived examples.

Install and verify a daily operating-system scheduled task on the designated reviewer machine that runs:

```sh
node scripts/purge-expired-copies.mjs /private/path/sudoku-buddy-reports
```

The cleanup removes the entire receipt folder, including any nested reviewed copies, on or before its recorded KV expiry. A missing or malformed deadline causes that receipt folder to be deleted immediately. On its first run against the old flat-export format, it removes all receipt-shaped legacy ZIPs/folders immediately because they have no trustworthy local deadline; it stops on unrelated or untracked files. Check the scheduled task's result every day and keep its failure alert actionable. If the task is disabled or the reviewer machine is unavailable, stop exporting and resume only after cleanup succeeds. Do not claim operational 90-day compliance until the workspace, schedule, owner, and existing-export inventory have been checked.

For a deletion request, collect every receipt shown in **Submission receipts** (corrected revisions have separate receipts) and run this for each one:

```sh
node scripts/delete-report.mjs <receipt> /private/path/sudoku-buddy-reports
```

For a deletion request, use a separately issued Cloudflare token scoped to read and write KV keys/values; do not leave write access in the exporter's environment. The command deletes the KV key, verifies the direct Cloudflare API returns 404, and removes the matching managed review folder and any legacy flat `<receipt>.zip`. It reports an incomplete deletion if either side fails; keep the request open and retry/verify before closing it. The designated workspace is the only permitted export location, so its receipt folder is the complete Freevia-controlled local-copy inventory. A one-off `wrangler kv key delete` is insufficient by itself. Deleting a report never removes the user's on-device receipt or original puzzle history.

## Privacy and operational limits

The Worker does not log report requests, store client IP addresses, keep report metadata, or expose a read route. The rate-limit key is a fixed shared value and contains no IP. `observability.enabled = false` disables Worker observability configured here; Cloudflare may separately process network and security telemetry under its own service terms. KV is eventually consistent. Expiration and direct deletion remove the KV object, but propagation through Cloudflare's network can take time; the app exposes no report retrieval route, and deletion requests are verified against the direct KV API. No policy can verify or delete copies that a reviewer places outside the designated workspace; that is prohibited and must be included in workspace onboarding and audits.

The ZIP signature check does not validate archive members or protect a reviewer from malformed ZIP bombs. Keep the hard cap, use offline tools, and never automatically extract untrusted files. Workers KV is suitable only for the initial low-volume bounded queue: it has eventual consistency, bounded account storage, and no atomic conditional writes. Do not move reports into a longer-lived object store or create a durable training dataset.

## Local tests

```sh
npm test
```

Tests use an in-memory KV/rate-limit mock and do not contact Cloudflare.
