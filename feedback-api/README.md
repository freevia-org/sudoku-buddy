# Sudoku Buddy private feedback intake

This is the private opt-in upload endpoint for Sudoku Buddy report ZIP files. It is deployed separately from the Android build at the URL configured in the release build. The endpoint has no read/list API and returns only an opaque SHA-256 receipt. The Android integration is implemented on the release-preparation branch; physical-device qualification and publication of the matching privacy disclosures remain release gates.

## Storage and limits

- `POST /v1/reports` accepts only `application/zip` with a decimal `Content-Length` that exactly matches the streamed body.
- The body is read incrementally and stopped as soon as it exceeds the declared length or the 20 MiB hard cap. It is buffered only after bounded collection so it can be hashed and written to Workers KV.
- The first four bytes must match a ZIP local-file, empty-archive, or spanning signature. This is a quick format check, not a full ZIP safety scan; moderators must treat all report contents as untrusted.
- The SHA-256 digest is the receipt and key (`reports/<sha256>.zip`), making identical submissions idempotent. No submitter name, email, app installation ID, timestamp, or source IP is stored in a separate record.
- Entries receive a 90-day `expirationTtl`. KV expiration is enforced by Cloudflare; remove a report earlier by deleting its key in the private KV namespace. Do not claim a hard deletion time beyond the configured TTL without checking Cloudflare's current deletion semantics.
- The KV account value limit is 25 MiB; this service caps values at 20 MiB. This queue is intended for a bounded, low-volume pilot. KV is eventually consistent and does not provide an atomic compare-and-set; identical simultaneous submissions still converge on the same content-addressed key, but only one request may report `accepted`.
- The rate limiter uses a single account-wide counter (30 calls per 60 seconds), so it never needs a client IP key. It is deliberately coarse and shared by all users; Cloudflare describes this binding as a soft limit, not a precise abuse-control or quota system.

## Create and deploy

Prerequisites: Node.js 20 or later, Wrangler 4.36.0 or later (for `ratelimits`), a Cloudflare account with Workers and Workers KV enabled, and an account-scoped API token that can deploy this Worker and access only the intake namespace. No credentials belong in this folder or repository.

The private namespace and Worker are already created and deployed. The endpoint is `https://sudoku-buddy-feedback.antoni-ivanov.workers.dev/v1/reports`; the private KV namespace ID and rate-limit configuration are recorded in `wrangler.toml`. A synthetic ZIP was accepted, an identical retry returned the same receipt, and the synthetic object was deleted. Do not send corpus or personal test photos during infrastructure checks. Future deployments require an authorized Wrangler session and a review of the current account configuration.

## Private review and export

There is no public report retrieval route. A trusted reviewer with a Cloudflare API token scoped to read keys and values in this namespace may export the queue to a private machine. Set `CF_ACCOUNT_ID`, `CF_KV_NAMESPACE_ID`, and `CLOUDFLARE_API_TOKEN` in that machine's process environment, then run:

```sh
node scripts/export-reports.mjs /private/path/sudoku-buddy-reports
```

The exporter lists only `reports/` keys, downloads raw values, checks each receipt against the SHA-256 of its ZIP bytes, verifies a ZIP signature, and creates files with restrictive permissions without overwriting existing files. Review archives offline, import only reviewed examples into the corpus, remove identifying material if present, and delete the corresponding KV key as soon as the report is no longer needed. Securely delete local exported copies after review. Never commit user submissions to the public repository or enable public directory/object listing.

For a one-off deletion, use `npx wrangler kv key delete 'reports/<receipt>.zip' --namespace-id <namespace-id> --remote`. Deleting KV values does not remove any previously exported copy.

## Privacy and operational limits

The Worker does not log report requests, store client IP addresses, keep report metadata, or expose a read route. The rate-limit key is a fixed shared value and contains no IP. `observability.enabled = false` disables Worker observability configured here; Cloudflare may separately process network and security telemetry under its own service terms. The app integration is implemented, but physical-device qualification and publication of matching privacy and Play Data safety disclosures remain required before release.

The ZIP signature check does not validate archive members or protect a reviewer from malformed ZIP bombs. Keep the hard cap, use offline tools, and never automatically extract untrusted files. Workers KV is suitable only for the initial low-volume bounded queue: it has eventual consistency, bounded account storage, and no atomic conditional writes. Move to R2 or another object store with access controls and lifecycle rules before needing a durable or higher-volume training dataset.

## Local tests

```sh
npm test
```

Tests use an in-memory KV/rate-limit mock and do not contact Cloudflare.
