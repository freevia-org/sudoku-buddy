const MiB = 1024 * 1024;
export const MAX_REPORT_BYTES = 20 * MiB;
export const REPORT_TTL_SECONDS = 90 * 24 * 60 * 60;
const REPORT_PREFIX = "reports/";

function json(status, body, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...headers },
  });
}

function isZipSignature(bytes) {
  if (bytes.length < 4) return false;
  // Ordinary ZIP local-file header, empty-archive end record, or spanning marker.
  return (
    (bytes[0] === 0x50 && bytes[1] === 0x4b && bytes[2] === 0x03 && bytes[3] === 0x04) ||
    (bytes[0] === 0x50 && bytes[1] === 0x4b && bytes[2] === 0x05 && bytes[3] === 0x06) ||
    (bytes[0] === 0x50 && bytes[1] === 0x4b && bytes[2] === 0x07 && bytes[3] === 0x08)
  );
}

async function readBoundedBody(request, expectedLength) {
  if (!request.body) return null;
  const reader = request.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > MAX_REPORT_BYTES || total > expectedLength) {
        await reader.cancel("request body exceeds declared or allowed size");
        return null;
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  if (total !== expectedLength) return null;

  const body = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return body;
}

async function sha256Hex(bytes) {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname !== "/v1/reports") return json(404, { error: "not_found" });
    if (request.method !== "POST") return json(405, { error: "method_not_allowed" }, { allow: "POST" });

    // One account-wide counter avoids receiving or persisting client IP addresses.
    // Cloudflare's rate-limit binding is intentionally only a coarse abuse brake.
    if (!env.SUBMISSION_LIMITER) return json(503, { error: "submission_temporarily_unavailable" });
    let success;
    try {
      ({ success } = await env.SUBMISSION_LIMITER.limit({ key: "all-report-submissions" }));
    } catch {
      return json(503, { error: "submission_temporarily_unavailable" });
    }
    if (!success) return json(429, { error: "rate_limited" }, { "retry-after": "60" });

    if (request.headers.get("content-type") !== "application/zip") {
      return json(415, { error: "content_type_must_be_application_zip" });
    }

    const contentLengthHeader = request.headers.get("content-length");
    if (contentLengthHeader === null || !/^(0|[1-9][0-9]*)$/.test(contentLengthHeader)) {
      return json(411, { error: "exact_content_length_required" });
    }
    const contentLength = Number(contentLengthHeader);
    if (!Number.isSafeInteger(contentLength) || contentLength <= 0) {
      return json(400, { error: "invalid_content_length" });
    }
    if (contentLength > MAX_REPORT_BYTES) return json(413, { error: "report_too_large" });

    const bytes = await readBoundedBody(request, contentLength);
    if (!bytes) return json(400, { error: "body_length_mismatch" });
    if (!isZipSignature(bytes)) return json(415, { error: "zip_signature_required" });

    const digest = await sha256Hex(bytes);
    const key = `${REPORT_PREFIX}${digest}.zip`;
    try {
      // The digest is both the receipt and the content-addressed dedup key. Concurrent
      // identical uploads can only replace the same bytes under the same key.
      const existing = await env.REPORTS.get(key, { type: "arrayBuffer" });
      if (existing === null) {
        await env.REPORTS.put(key, bytes, { expirationTtl: REPORT_TTL_SECONDS });
      }
      return json(existing === null ? 201 : 200, {
        receipt: digest,
        status: existing === null ? "accepted" : "already_received",
      });
    } catch {
      // Do not include request data or platform errors in logs or responses.
      return json(503, { error: "submission_temporarily_unavailable" });
    }
  },
};

export const __test = { isZipSignature, readBoundedBody, sha256Hex };
